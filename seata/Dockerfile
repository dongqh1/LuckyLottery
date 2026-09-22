FROM apache/seata-server:2.5.0.jdk17

ARG MYSQL_CONNECTOR_VERSION=9.7.0
ARG MYSQL_CONNECTOR_SHA256=0353648eaa1c91e0f4020c959abf756bc866ffd583df22ae6b6f6e0cbd43eb44

RUN curl -fsSL "https://repo.maven.apache.org/maven2/com/mysql/mysql-connector-j/${MYSQL_CONNECTOR_VERSION}/mysql-connector-j-${MYSQL_CONNECTOR_VERSION}.jar" \
        -o "/seata-server/libs/mysql-connector-j-${MYSQL_CONNECTOR_VERSION}.jar" \
    && echo "${MYSQL_CONNECTOR_SHA256}  /seata-server/libs/mysql-connector-j-${MYSQL_CONNECTOR_VERSION}.jar" \
        | sha256sum --check
