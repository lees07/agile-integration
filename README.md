# agile-integration-homework

作业验证步骤：
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
在./agile-integration-homework/core/services/integration-test-server目录中运行
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

