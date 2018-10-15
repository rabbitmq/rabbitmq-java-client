@GrabResolver(name = 'rabbitmq-bintray', root = 'http://dl.bintray.com/rabbitmq/maven')
@GrabResolver(name = 'rabbitmq-packagecloud-milestones', root = 'https://packagecloud.io/rabbitmq/maven-milestones/maven2')
@Grab(group = 'com.rabbitmq', module = 'amqp-client', version = '${version}')
@Grab(group = 'org.slf4j', module = 'slf4j-simple', version = '1.7.25')
import com.rabbitmq.client.AMQP
import com.rabbitmq.client.Channel
import com.rabbitmq.client.ConnectionFactory
import com.rabbitmq.client.DefaultConsumer
import com.rabbitmq.client.Envelope
import org.slf4j.LoggerFactory

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

def connection = new ConnectionFactory().newConnection()
try {
    Channel ch = connection.createChannel()
    def queue = ch.queueDeclare().getQueue()
    CountDownLatch latch = new CountDownLatch(1);
    ch.basicConsume(queue, true, new DefaultConsumer(ch) {
        @Override
        void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
            latch.countDown()
        }
    })
    ch.basicPublish("", queue, null, "test".getBytes())
    def received = latch.await(5, TimeUnit.SECONDS)
    if (!received)
        throw new IllegalStateException("Didn't receive message in 5 seconds")
    LoggerFactory.getLogger("rabbitmq").info("Test succeeded")
    System.exit 0
} catch (Exception e) {
    LoggerFactory.getLogger("rabbitmq").info("Test failed", e)
    System.exit 1
}
