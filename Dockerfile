FROM eclipse-temurin:17-jre
WORKDIR /app

# 1. non-root 전용 유저 및 그룹 생성 (appuser)
RUN groupadd -r appuser && useradd -r -g appuser appuser

# 2. 작업 디렉토리(/app)의 소유권을 appuser로 변경
RUN chown -R appuser:appuser /app

# 3. 호스트(로컬 PC)에서 이미 빌드된 jar 파일을 복사
COPY --chown=appuser:appuser build/libs/*-SNAPSHOT.jar app.jar

# 4. 컨테이너 실행 권한을 appuser로 전환
USER appuser

EXPOSE 19900

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
