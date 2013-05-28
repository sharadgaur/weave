What is Weave ?
===============

Weave is a simple set of libraries that allows you to easily manage distributed applications through an abstraction layer built on Apache YARN. Weave allows you to use YARN’s distributed capabilities with a programming model that is similar to running threads. Weave is NOT a replacement for Apache YARN.  It is instead a value-added framework that operates on top of Apache YARN.

Why do I need Weave ?
=====================
Weave dramatically simplifies and reduces your development efforts, enabling you to quickly and easily manage 
your distributed applications through its simplified abstraction layer built on YARN. YARN can be quite difficult to use and requires a large ramp up effort since it is built specifically for MapReduce and is typically meant for managing batch jobs. YARN, however, can be used as a generalized custom resource management tool that can run any type of job.  In additon to running batch jobs, cluster can be used for running real time jobs and long running job. Unfortunately, YARN’s capabilities are too low level to allow you to quickly develop an application, requiring a great deal of boilerplate code even for simple applications.  Additionally, its logging output is not available until the application is finished. This becomes a serious challenge when managing long running jobs: since those jobs never finish you cannot view the logs, which makes it very difficult to develop and debug such applications. Finally, YARN does not provide standard support for application lifecycle management, communication between containers and the Application Master, and handling application level errors.

Continuuity Weave provides you with the following benefits:

  * A simplified API for specifying, running and managing applications
  * A simplified way to specify and manage the stages of the application lifecycle
  * A generic Application Master to better support simple applications
  * Log & metrics aggregation for application
  * Simplified archive management
  * Improved control over application logs, metrics and errors
  * Discovery service

Getting Started
===============
To build weave library

<pre>
 $ git clone http://github.com/continuuity/weave.git
 $ cd weave
 $ mvn install
</pre>


Quick Example
=============

Let's take a simple example of building an `EchoServer` in Weave. Traditionally, when you build a server as simple 
as this, you add logic within a `Runnable` implementation to run it in within a `Thread` using appropriate `ExecutorService`.

<pre>
public class EchoServer implements Runnable {
   private static Logger LOG = LoggerFactory.getLogger(EchoServer.class);
   private final ServerSocket serverSocket;

   public EchoServer() {
     ...
   }

   @Override
   public void run() {
     while ( isRunning() ) {
       Socket socket = serverSocket.accept();
       ...
     }
   }
}
</pre>

Defines an implementation of `Runnable` that implements method `run`. Now, the `EchoServer` which is a `Runnable` can be 
executed by `ExecutorService` in a `Thread`.

<pre>
...
ExecutorService service = Executors.newFixedThreadPool(2);
service.submit(new EchoServer());
...
</pre>

The above model is something we are all accustomed to.  However, assume you want to run this on a YARN cluster. In order to run on the YARN cluster, you implement `WeaveRunnable` similar to implementing `Runnable`. 

**Implement Runnable**
<pre>
public class EchoServer implements WeaveRunnable {
   private static Logger LOG = LoggerFactory.getLogger(EchoServer.class);
   private final ServerSocket serverSocket;
   private final int port;

   public EchoServer() {
     ...
   }

   @Override
   public void run() {
     while ( isRunning() ) {
       Socket socket = serverSocket.accept();
       ...
     }
   }
}
</pre>

`AbstractWeaveRunnable` implements `WeaveRunnable` that implements `Runnable`.By doing this you can run a `WeaveRunnable` implementation within a `Thread` and also in a container on a YARN cluster. In order to run `EchoServer` on the YARN cluster, you must first create a `WeaveRunnerService` which is similar to `ExecutorService`. To run on the YARN cluster you need the YARN cluster configuration and connection string to a running instance of zookeeper service.

**Starting YARN Runner Service**
<pre>
  WeaveRunnerService runnerService = new YarnWeaveRunnerService(new YarnConfiguration(), 
                                                                zkServer.getConnectionString());
  runnerService.startAndWait();
</pre>

Now that we have initialized `WeaveRunnerService` you can prepare to run the `EchoServer` on the YARN cluster. In preparation to run `EchoServer` on the YARN cluster we will attach a log handler that ensures all logs generated by `EchoServer` across all nodes in the cluster are centralized on the client. 

**Preparing to run WeaveRunnable**
<pre>
WeaveController controller = runnerService.prepare(new EchoServer())
                               .addLogHandler(new PrinterLogHandler(new PrintWriter(System.out)))
                               .start();
</pre>

Now that you have started, prepared and launched `EchoServer` to run on the YARN cluster, you can attach listeners that allow you to observe state transitions in your application.

**Attaching Listeners for state transitions**
<pre>
controller.addListener(new ListenerAdapter() {
   @Override
   public void running() {
     LOG.info('Echo Server Started');
   }
}
</pre>

In order to stop the running `EchoServer`, you use the controller returned during the start of the application to stop it, as follows:

**Stopping WeaveRunnable**
<pre>
  controller.stop().get();
</pre>

This will shutdown the application master and all the containers configured during prepare. Note, in the code above you do not need to specify the archives
that need to be shipped to remote machines on the YARN cluster (were the container will run). It's all taken care by Weave.

Advanced Examples
=================

Discovery Service
-----------------
What use is the `EchoServer` if it's not discoverable? Meaning, if clients who want to access the server running in the cluster are not able to connect to the service and talk to it, the purpose of the `EchoServer` is defeated. Weave address this issue by exposing a discovery service that allows Weave application to announce themselves on the cluster and the client can discover and connect to running applications. Let's add that capability to the `EchoServer`. In order to do so, first the `EchoServer` will have to start on a port that's available on the machine it is started on, and then announce it's presence via the Weave discovery service API. 

**WeaveRunnable with Discovery Announce**
<pre>
public class EchoServer extends AbstractWeaveRunnable {
  private static final Logger LOG = LoggerFactory.getLogger(EchoServer.class);
 
  @Override
  public void initialize(WeaveContext context) {
    super.initialize(context);
    ...
    try {
      serverSocket = new ServerSocket(0); // start on any available port.
      context.announce("echo", serverSocket.getLocalPort());
    } catch (IOException e) {
      throw Throwables.propogate(e);
    }
  }

  @Override
  public void run() {
    ...
  }
}
</pre>

During the initialization phase of the container, using `WeaveContext` (the port that `EchoServer` was started on) was announced. Next, clients should be able to discover the _echo_ service that is started.

**Client Discovery**
<pre>
  ...
  WeaveController controller = ....
  ... 
  Iterable<Discoverable> echoServices = controller.discoverService("echo");
  ...
  for(Discoverable discoverable : echoServices) {
    Socket socket = new Socket(discoverable.getSocketAddress().getAddress(), 
                               discoverable.getSocketAddress().getPort());
    ...
  }
</pre>

Logging
-------
In the above examples we have seen that when preparing to run a implementation of `WeaveRunnable` we attach the log handler. It is collecting all logs emitted by the containers that are returned to the client to take an action. This way, you don't have to leave your IDE to debug the application you are running on the YARN cluster. Within the container, you use a standard SLF4J logger to log messages.  They are hijacked and sent through the Kafka broker to the client. With every application that is launched, an additional container which is Kafka broker is also launched.   

**SLF4J logger for logging**
<pre>
public class EchoServer extends AbstractWeaveRunnable {
  private static final Logger LOG = LoggerFactory.getLogger(EchoServer.class);
  ...
  @Override
  public void run() {
    ...
    LOG.info('New client accepted');
    ...
  }
  ...
}
</pre>

Resource Specification
----------------------
While you prepare implementation of `WeaveRunnable` to run on the YARN cluster, you provide specification of resources to be used to run the container. Assets like number of cores to be used, amount of memory and number of instances can be specified. This internally will use Cgroups to limit the amount of system resources used by the container.

**Specifying Resource Constraints for Container**
<pre>
WeaveController controller = runnerService.prepare(new EchoServer(port),
                                                   ResourceSpecification().Builder().with()
                                                   .setCores(1)
                                                   .setMemory(1, ResourceSpecification.SizeUnit.GIGA)
                                                   .setInstances(2).build())
                                                  .addLogHandler(new PrinterLogHandler(new PrintWriter(System.out)))
                                                  .start();
</pre>

Archive Management
------------------
In order to run in a container on the YARN cluster, all the necessary jars have to be marshalled to the node the container is running on. This is all internally handled by Weave, but the APIs also allow you to specify additional files during prepare to be marshalled to the container where it's running.

Application
-----------
A `WeaveApplication` is a collection of distributed `WeaveRunnable` stiched together. It can be better described with an example. Let's say you have a web application that you would like to deploy on a cluster that's running YARN. In order to do so, you need instances of jetty server and all associated files to serve the application.

**Specifying WeaveApplication**
<pre>
public class WebApplication implements WeaveApplication {
  @Override
  public WeaveSpecification configure() {
    return WeaveSpecification().Builder.with()
      .setName("My Web Application")
      .withRunnables() 
         .add(new JettyWebServer())
         .withLocalFiles()
            .add("html-pages.tgz", pages, true)
         .apply()
         .add(new LogsCollector())
      .anyOrder()
      .build();
  }
}
</pre>

Once you have defined an application in Weave, you can run it the same way you would run a `WeaveRunnable`. It's as simple as that. If you look at the above example closely, weave applications support the order in which the `WeaveRunnables` are started on the cluster. The above example specifies no order, so all the `WeaveRunnables` can start concurrently. But you can modify the behavior as follows:

**Ordering**
<pre>
public class WebApplication implements WeaveApplication { 
  @Override
  public WeaveSpecification configure() {
    return WeaveSpecification().Builder.with()
      .setName("My Web Application")
      .withRunnables() 
         .add("jetty", new JettyWebServer())
         .withLocalFiles()
            .add("html-pages.tgz", pages, true)
         .apply()
         .add("log", new LogsCollector())
      .order()
         .first("log")
         .next("jetty")
      .build();
  }
}
</pre>

Documentation & Talks
======================

API
---
   * [Weave Doc Index](http://continuuity.github.io/weave/apidocs/index.html "Weave Doc Index")
   * [Weave API](http://continuuity.github.io/weave/apidocs/com/continuuity/weave/api/package-summary.html "Weave API")
   * [Weave YARN](http://continuuity.github.io/weave/apidocs/com/continuuity/weave/yarn/package-summary.html "Weave YARN")
   * [Weave Common](http://continuuity.github.io/weave/apidocs/com/continuuity/weave/common/package-summary.html "Weave Common")
   * [Weave Discovery](http://continuuity.github.io/weave/apidocs/com/continuuity/weave/discovery/package-summary.html "Weave Discovery")
   * [Weave Zookeeper](http://continuuity.github.io/weave/apidocs/com/continuuity/weave/zookeeper/package-summary.html "Weave Zookeeper")
   
Talks
-----
   * [Weave Introduction](http://continuuity.github.io/weave/talks/Weave-Talk-v1.0.pdf "Weave Introduction")
   
Community
=================

How to Contribute
-----------------
Are you interested in making Weave better? Our development model is a simple pull based model with a consensus 
building phase - similar to the [Apache's voting process](http://www.apache.org/foundation/voting.html "Apache Voting Process"). If you think that you help make Weave better, add new 
features or fix bugs in Weave or even if you have an idea on how to improve something that's already there in Weave, here's
how you can do that. 

  * Fork [weave](https://github.com/continuuity/weave "weave") into your own GitHub repository
  * Create a topic branch with an appropriate name 
  * Work on your favourite feature to your content
  * Once you are satisifed, create a pull request by going [continuuity/weave](https://github.com/continuuity/weave "continuuity/weave") project. 
  * Address all the review comments
  * Once addressed, the changes will be committed to the [continuuity/weave](https://github.com/continuuity/weave "continuuity/weave") repo. 

Groups
------
  * User Group: [weave-user](https://groups.google.com/d/forum/weave-user "weave-user")

License
=======
Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance 
with the License. You may obtain a copy of the License at

<http://www.apache.org/licenses/LICENSE-2.0>

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed 
on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for 
the specific language governing permissions and limitations under the License.
