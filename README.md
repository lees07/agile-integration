# agile-integration-homework

## 作业验证步骤：
0. 本地有OpenJDK v1.8、Maven v3.5.4、A-MQ v7.2.2和curl-7.29.0-42.el7_4.1  

1. 下载作业的源码工程
```
git clone https://github.com/lees07/agile-integration-homework.git
```

2. 编译源码工程  
在./agile-integration-homework/parent目录中运行
```
mvn clean install
```
编译parent工程。  

在./agile-integration-homework/core目录中运行
```
mvn clean install
```
编译Artifacts、Integration-Test-Server、Inbound、XLate和Outbound工程。  

注，我更新了fuse的版本号到630356以修复bug，请参考 https://access.redhat.com/solutions/3478681

3. 启动作业的运行环境  
启动A-MQ  
在本地的A-MQ运行时amq-local/bin目录中运行
```
./artemis run
```

启动Integration-Test-Server  
在 ./agile-integration-homework/core/services/integration-test-server 目录中运行
```
mvn camel:run
```

启动Inbound  
在./agile-integration-homework/core/inbound目录中运行
```
mvn camel:run
```

启动XLate  
在./agile-integration-homework/core/xlate目录中运行
```
mvn camel:run
```

启动Outbound  
在./agile-integration-homework/core/outbound目录中运行
```
mvn camel:run
```

4. 验证作业  
在./agile-integration-homework/core/inbound/src/test/data目录中运行
```
curl -X POST -H "Content-Type: application/xml" --data @./SimplePatient.xml http://localhost:9098/cxf/demos/match
```
向正确的URL发送正确的报文，验证Inbound、XLate和Outbound运行正确，Integration-Test-Server接收到报文，返回DONE结果。  

运行
```
curl -X POST -H "Content-Type: application/xml" --data @./SimplePatient.xml http://localhost:9098/cxf/demos/other
```
向错误的URL发送正确的报文，验证Inbound正确处理报文，返回NO MATCH的结果。  

运行
```
curl -X POST -H "Content-Type: application/xml" --data @./BadPatientInfo.xml http://localhost:9098/cxf/demos/match
```
向正确的URL发送错误的报文，验证Inbound正确处理报文，返回MATCH的结果。  


## 实现思路说明：

根据 https://www.opentlc.com/labs/agile_integration_advanced/06_1_Assignment_Lab.html 的指示  
业务需求是将DEIM系统与Nextgate系统集成，其中涉及到系统之间的协议转换、报文转换、异步处理和异常处理等环节。  
其中：  
1. Inbound  
该 camel 工程通过 xml 类型的 spring dsl 实现，配置文件是 src/main/resources/META-INF/spring/camelContext.xml  
通过JAXRS类型的服务，获取到病人的XML数据，并进行处理。
```
   <bean class="com.redhat.usecase.service.impl.DEIMServiceImpl" id="demographicImpl"/>
   <jaxrs:server address="http://localhost:9098/cxf/demos" id="demoGraphicsService">
      <jaxrs:serviceBeans>
         <ref bean="demographicImpl"/>
      </jaxrs:serviceBeans>
   </jaxrs:server>

```
以上代码定义了 JAXRS 服务的地址，以及处理该服务的 Bean 是 com.redhat.usecase.service.impl.DEIMServiceImpl  

在 DEIMServiceImpl 的代码中定义了 camel 的入口点 direct:integrateRoute ，并根据访问路径，在 header 中设置了 METHOD 属性，该属性在 camel 中做为路由的选择依据。如下代码所示。
```
	@Produce(uri = "direct:integrateRoute")
	ProducerTemplate template;

	@Override
	@POST
	@Path("/match")
	@Consumes(MediaType.APPLICATION_XML)
	public Response addPerson(Person person) {
		// This header is used to direct the message in the Camel route
		Map<String, Object> headers = new HashMap<String, Object>();
		headers.put("METHOD", "match");
		...
	}

```
并从camel流程中获得处理的状态，转换成对应的返回值。如下代码所示。
```
	if (camelResponse.equals("0")) {
		comment = "NO MATCH";
	} else if (camelResponse.equals("1")) {
		comment = "MATCH";
	} else if (camelResponse.equals("2")) {
		comment = "DONE";
	} else {
		comment = "ERROR";
	}
	esbResponse.setComment(comment);

```
在 camel 流程中，从 direct:integrateRoute 获取数据，根据 METHOD 属性进行选择，如果是 'match' 则进行序列化处理，并且判断内容是否合规。  
若数据合规，则将报文发到 activemq:queue:q.empi.deim.in ，然后到 done 路由中。若数据没有 legalname ，则到 match 路由中。  
若 METHOD 不是 'match' 则到 unmatch 路由中处理。如下代码所示。
```
      <route id="handleRest">
         <from id="_from1" uri="direct:integrateRoute"/>
         <log id="_log1" loggingLevel="INFO" message="rest headers: ${headers}"/>
         <choice id="_choice1">
            <when id="_when11">
               <xpath>$METHOD = 'match'</xpath>
               <marshal id="_marshal1" ref="personFormat"/>
               <log id="_log2" loggingLevel="INFO" message="after marshal: ${body}"/>
               <choice id="_choice2">
                  <when id="_when21">
                     <simple>${bodyAs(String)} contains 'legalname'</simple>
                     <inOnly id="_to1" uri="activemq:queue:q.empi.deim.in"/>
                     <to id="_to2" uri="direct:done"/>
                  </when>
                  <otherwise id="_otherwise2">
                     <to id="_to3" uri="direct:match"/>
                  </otherwise>
               </choice>
            </when>
            <otherwise id="_otherwise1">
               <to id="_to4" uri="direct:unmatch"/>
            </otherwise>
         </choice>
      </route>

```
注，Queue 的操作是单向的，因此使用 inOnly ，并且在 uri 中指定 queue 的类型，避免 A-MQ 在自动创建时类型错误。  

路由 done 、 match 、 unmatch 、 error 按要求将 body 的内容转换为 DEIMServiceImpl 中需要的内容。如下代码所示。
```
      <route id="processDone">
         <from id="_from2" uri="direct:done"/>
         <transform id="_transform1">
            <constant>2</constant>
         </transform>
      </route>
      <route id="processMatch">
      ...
```

2. XLate  
从 Queue 中消费数据。如下代码所示。
```
      <route id="translate" streamCache="true">
         <from id="_from1" uri="activemq:queue:q.empi.deim.in"/>
         <log id="_log1" loggingLevel="INFO" message="receive msg from q: ${body}"/>
         <to id="_to1" uri="direct:convert"/>
      </route>

```
然后在 convert 路由中进行，进行反序列化、类型转换和序列化。若转换成功，则将报文发到 activemq:queue:q.empi.nextgate.out 。若转换异常，则重试3次，不成功则将报文发到 activemq:queue:q.empi.transform.dlq 。如下代码所示。
```
      <route id="convert" streamCache="true">
         <from id="_from2" uri="direct:convert"/>
         <unmarshal id="_unmarshal1" ref="personFormat"/>
         <convertBodyTo id="_convertBodyTo1" type="com.sun.mdm.index.webservice.ExecuteMatchUpdate"/>
         <!-- redelivery 3 times -->
         <log id="_log2" loggingLevel="INFO" message="after convert: ${body}"/>
         <marshal id="_marshal1" ref="nextgateFormat"/>
         <log id="_log3" loggingLevel="INFO" message="after translate: ${body}"/>
         <inOnly id="_to2" uri="activemq:queue:q.empi.nextgate.out"/>
         <onException id="_onException1">
            <exception>org.apache.camel.TypeConversionException</exception>
            <redeliveryPolicy maximumRedeliveries="3" redeliveryDelay="0"/>
            <log id="_log3" loggingLevel="INFO" message="on error: ${body}"/>
            <inOnly id="_to3" uri="activemq:queue:q.empi.transform.dlq"/>
         </onException>
      </route>

```

3. Outbound  
从 Queue 中消费数据。将报文临时保存到 bodyMsg 中。如下代码所示。
```
      <route id="sendToNextGate" streamCache="true">
         <from id="_from1" uri="activemq:queue:q.empi.nextgate.out"/>
         <log id="_log1" loggingLevel="INFO" message="receive msg from q: ${body}"/>
         <setProperty id="_setProperty1" propertyName="bodyMsg">
            <simple>${body}</simple>
         </setProperty>
         <to id="_to1" uri="direct:convert"/>
      </route>

```
将报文反序列化为 ExecuteMatchUpdate 对象，然后转换为 executeMatchUpdate 方法的两个参数。调用 cxf:bean:nextgateService 。  
若调用错误，则重试3次，不成功则将保存在 bodyMsg 的报文发到 activemq:queue:q.empi.nextgate.dlq 。
```
      <route id="convert" streamCache="true">
         <from id="_from2" uri="direct:convert"/>
         <unmarshal id="_unmarshal1" ref="nextgateFormat"/>
         <log id="_log3" loggingLevel="INFO" message="after unmarshal: ${body}"/>
         <convertBodyTo id="_convertBodyTo1" type="java.util.List"/>
         <log id="_log4" loggingLevel="INFO" message="after convert: ${body}"/>
         <!-- redelivery 3 times -->
         <to id="_to2" uri="cxf:bean:nextgateService?defaultOperationName=executeMatchUpdate"/>
         <onException id="_onException1">
            <exception>java.net.ConnectException</exception>
            <redeliveryPolicy maximumRedeliveries="3" redeliveryDelay="0"/>
            <transform id="_transform1">
               <simple>${exchangeProperty[bodyMsg]}</simple>
            </transform>
            <log id="_log2" loggingLevel="INFO" message="on error: ${body}"/>
            <inOnly id="_to3" uri="activemq:queue:q.empi.nextgate.dlq"/>
         </onException>
      </route>

```

## 一些想法：
这是一个典型的应用集成场景。  
之前的实现的方法是 camel 是在 karaf 中通过 osgi ，将以上各工程做为 features 集成在一起，运行在一个 jvm 实例中。  
其它的企业服务总线软件也是类似，将所有的流程集成在一起。即使是集群部署，各个节点部署的是所有的服务和流程。  
这造成了企业服务总线软件本身过于复杂，同时集成项目也过于复杂。  
敏捷集成是要将集成的流程适当分散化，将不同的工程在不同的 jvm 中运行，类似于微服务的思想。  
下一步，希望有如下的改进：  
1. Camel 工程的最小化、独立化的配置。减少不必要的类库的引用，进一步降低每个工程的复杂度。每个工程应该独立且并列，而不是层次依赖的，服务接口定义和规范等置于公共的工程中被其它工程引用。每个工程应该独立运行；  
2. 增加自动部署到 Openshift 环境的方法，方便部署到云环境；  
3. 增加一个可以看到所有工程的集成流程的全貌的功能，方便总览分布部署的敏捷集成的应用；  
4. 增加在线测试等功能。  
