/*
 * The MIT License
 *
 * Copyright (c) 2019 CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package jenkins.security.seed;

import com.gargoylesoftware.htmlunit.Cache;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.model.User;
import jenkins.benchmark.jmh.JmhBenchmark;
import jenkins.benchmark.jmh.JmhBenchmarkState;
import org.junit.Test;
import org.junit.runner.Description;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;
import org.jvnet.hudson.test.TestEnvironment;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.xml.sax.SAXException;
import test.security.realm.InMemorySecurityRealm;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@JmhBenchmark
@Warmup(iterations = 3)
@Threads(24)
@Fork(2)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class UserSeedPropertyBenchmark {

    @Test
    public void runJmhBenchmarks() throws RunnerException, IOException, InterruptedException {
        final TestEnvironment env = new TestEnvironment(Description.createTestDescription(getClass(), "runJmhBenchmarks"));
        final Options options = new OptionsBuilder()
                .mode(Mode.AverageTime)
                .shouldFailOnError(true)
                .shouldDoGC(true)
                .resultFormat(ResultFormatType.TEXT)
                .result("jmh-report.txt")
                .include(getClass().getName() + ".*")
                .build();
        try {
            env.pin();
            new Runner(options).run();
        } finally {
            env.dispose();
        }
    }

    public static class JenkinsState extends JmhBenchmarkState {
        public @Param("10000") int userCount;
        public List<User> users;

        @Override
        public void setup() throws Exception {
            final InMemorySecurityRealm realm = new InMemorySecurityRealm();
            getJenkins().setSecurityRealm(realm);
            users = IntStream.range(0, userCount)
                    .mapToObj(i -> "user_" + i)
                    .peek(realm::createAccount)
                    .map(User::getOrCreateByIdOrFullName)
                    .collect(Collectors.toList());
        }
    }

    @State(Scope.Thread)
    public static class UserState {
        public WebClient wc;

        @Setup
        public void login(final JenkinsState j) throws Exception {
            final String user = j.users.get(ThreadLocalRandom.current().nextInt(j.userCount)).getId();
            final JenkinsRule rule = new JenkinsRule() {
                {
                    jenkins = j.getJenkins();
                }

                @Override
                public URL getURL() throws IOException {
                    return new URL(Objects.requireNonNull(jenkins.getRootUrl()));
                }
            };
            wc = rule.createWebClient();
            final Cache cache = new Cache();
            cache.setMaxSize(0);
            wc.setCache(cache);
            wc.setJavaScriptEnabled(false);
            wc.setThrowExceptionOnFailingStatusCode(false);
            wc.getOptions().setPrintContentOnFailingStatusCode(false);
            wc.login(user, user, true);
        }
    }

    // TODO: try without remember me, see if it's around 340ms

    @Benchmark
    public HtmlPage visitPageWithRememberMe(final UserState u) throws IOException, SAXException {
        return u.wc.goTo("");
    }
}
