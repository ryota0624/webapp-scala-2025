FROM ghcr.io/graalvm/graalvm-community:23.0.2 AS stage0
LABEL snp-multi-stage="intermediate"
LABEL snp-multi-stage-id="ced8b539-1fad-465a-8c2e-c3c5fd3f2fe7"
WORKDIR /opt/docker
COPY 2/opt /2/opt
COPY 3/opt /3/opt
COPY 4/opt /4/opt
USER root
RUN ["chmod", "-R", "u=rX,g=rX", "/2/opt/docker"]
RUN ["chmod", "-R", "u=rX,g=rX", "/3/opt/docker"]
RUN ["chmod", "-R", "u=rX,g=rX", "/4/opt/docker"]
RUN ["chmod", "u+x,g+x", "/4/opt/docker/bin/webapp-scala"]

FROM ghcr.io/graalvm/graalvm-community:23.0.2 AS mainstage
USER root
RUN id -u demiourgos728 1>/dev/null 2>&1 || (( getent group 0 1>/dev/null 2>&1 || ( type groupadd 1>/dev/null 2>&1 && groupadd -g 0 root || addgroup -g 0 -S root )) && ( type useradd 1>/dev/null 2>&1 && useradd --system --create-home --uid 1001 --gid 0 demiourgos728 || adduser -S -u 1001 -G root demiourgos728 ))
WORKDIR /opt/docker
COPY --from=stage0 --chown=demiourgos728:root /2/opt/docker /opt/docker
COPY --from=stage0 --chown=demiourgos728:root /3/opt/docker /opt/docker
COPY --from=stage0 --chown=demiourgos728:root /4/opt/docker /opt/docker
ENV JAVA_TOOL_OPTIONS="-XX:+TieredCompilation -XX:TieredStopAtLevel=1 -Xss256k"
ENV OTEL_EXPORTER_OTLP_ENDPOINT="http://localhost:4317"
EXPOSE 8080
USER 1001:0
USER root
RUN ["mkdir", "classfiles"]
RUN ["chmod", "-R", "u+x,g+x", "classfiles"]
RUN ["java", "-Xshare:off", "-XX:DumpLoadedClassList=classfiles/mn.lst", "-jar", "lib/webapp-scala.jar", "warmup"]
RUN ["java", "-Xshare:dump", "-XX:SharedClassListFile=classfiles/mn.lst", "-XX:SharedArchiveFile=classfiles/mn13.jsa", "-jar", "lib/webapp-scala.jar"]
USER 1001:0
ENTRYPOINT ["jre/bin/java"]
CMD ["-XX:SharedArchiveFile=classfiles/mn13.jsa", "-jar", "lib/webapp-scala.jar"]
