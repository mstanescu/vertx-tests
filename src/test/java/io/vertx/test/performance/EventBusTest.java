package io.vertx.test.performance;

import io.vertx.core.*;
import io.vertx.test.testutils.TestBase;
import org.junit.Before;
import org.junit.Test;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.List;
import java.util.stream.Collectors;

import static io.vertx.test.testutils.AwaitUtils.awaitResult;


@SuppressWarnings("unused")
public class EventBusTest extends TestBase
{
    ThreadMXBean threadMXBean;

    @Before
    public void setup() throws Exception
    {
        vertx = awaitResult(
                h -> Vertx.clusteredVertx(
                        new VertxOptions(),
                        h
                )
        );

        threadMXBean = ManagementFactory.getThreadMXBean();
        threadMXBean.setThreadContentionMonitoringEnabled(true);
    }

    /**
     * Send and reply between verticles as fast as possible and measure rate
     */
    @Test
    public void testSendAndReply() throws Exception
    {
        String address = "testSendAndReply";
        int numberOfConsumerVerticles = 32;
        int numberOfSenderVerticles = 32;

        // deploy the consumers

        for (int i = 0; i < numberOfConsumerVerticles; i++)
        {
            String id = awaitResult(
                    h -> vertx.deployVerticle(
                            new AbstractVerticle()
                            {
                                @Override
                                public void start(Future<Void> startFuture) throws Exception
                                {
                                    vertx.eventBus().consumer(
                                            address,
                                            event -> event.reply(null)
                                    ).completionHandler(
                                            startFuture.completer()
                                    );
                                }
                            },
                            new DeploymentOptions(),
                            h
                    )
            );
        }

        for (int i = 0; i < numberOfSenderVerticles; i++)
        {
            String id = awaitResult(
                    h -> vertx.deployVerticle(
                            new AbstractVerticle()
                            {
                                @Override
                                public void start(Future<Void> startFuture) throws Exception
                                {
                                    startFuture.complete();

                                    doSend();
                                }

                                private void doSend()
                                {
                                    vertx.eventBus().send(
                                            address,
                                            new byte[16],
                                            event ->
                                            {
                                                doSend();
                                            }
                                    );
                                }
                            },
                            new DeploymentOptions(),
                            h
                    )
            );
        }

        Thread.sleep(10000);

        displayThreadInfo(threadMXBean);
    }

    /**
     * Use runOnContext to execute an empty function => loads 100% CPU
     *
     * @throws Exception
     */
    @Test
    public void testRunOnContext() throws Exception
    {
        String address = "testRunOnContext";
        int numberOfVerticles = 32;

        for (int i = 0; i < numberOfVerticles; i++)
        {
            String id = awaitResult(
                    h -> vertx.deployVerticle(
                            new AbstractVerticle()
                            {
                                @Override
                                public void start(Future<Void> startFuture) throws Exception
                                {
                                    startFuture.complete();
                                    doRunOnContext();
                                }

                                private void doRunOnContext()
                                {
                                    Vertx.currentContext().runOnContext(
                                            event ->
                                            {
                                                doRunOnContext();
                                            }
                                    );
                                }
                            },
                            new DeploymentOptions(),
                            h
                    )
            );
        }

        Thread.sleep(10000);

        displayThreadInfo(threadMXBean);
    }

    private void displayThreadInfo(ThreadMXBean threadMXBean)
    {
        List<Thread> threadIds = Thread.getAllStackTraces()
                                       .keySet()
                                       .stream()
                                       .filter(
                                               thread -> thread.getName().contains("vert.x-eventloop-thread")
                                       )
                                       .collect(Collectors.toList());

        for (Thread thread : threadIds)
        {
            ThreadInfo threadInfo = threadMXBean.getThreadInfo(thread.getId());

            System.out.println(String.format("ThreadInfo:%40s getBlockedTime:%5d msec getWaitedTime:%3d ",
                    thread.getName(),
                    threadInfo.getBlockedTime(),
                    threadInfo.getWaitedTime()
            ));
        }
    }
}
