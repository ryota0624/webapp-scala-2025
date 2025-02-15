TODO
~~1. test-container~~
2. zio-profiling
3. zio-telemetry
4. zio-prelude
5. zio-metrics-connector
6. zio-confi[Dockerfile](Dockerfile)g
7. auth middleware
8. wrap connection
9. use db connection
10. telemetry settingをapplication.confで切り替える

---*

java -Xshare:off -XX:DumpLoadedClassList=mn.lst  -jar /Users/ryota.suzuki/git/webapp-scala/webapp-scala/target/scala-3.3.5/webapp-scala-assembly-0.1.0-SNAPSHOT.jar
java -Xshare:dump -XX:SharedClassListFile=mn.lst -XX:SharedArchiveFile=mn13.jsa -jar /Users/ryota.suzuki/git/webapp-scala/webapp-scala/target/scala-3.3.5/webapp-scala-assembly-0.1.0-SNAPSHOT.jar
java -XX:SharedArchiveFile=mn13.jsa -jar /Users/ryota.suzuki/git/webapp-scala/webapp-scala/target/scala-3.3.5/webapp-scala-assembly-0.1.0-SNAPSHOT.jar 

https://www.graalvm.org/latest/reference-manual/native-image/metadata/

---

jlink
base imageの作成

base imageを引き継いでjib build

AppCDS実行

base imageを引き継いでjib build + AppCDS
