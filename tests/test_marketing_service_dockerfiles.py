import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SERVICES = (
    "lottery-service",
    "points-service",
    "coupon-service",
    "membership-service",
    "fulfillment-service",
)


class MarketingServiceDockerfileTests(unittest.TestCase):
    def test_each_service_builds_its_own_jar_with_the_existing_image_pattern(self):
        for service in SERVICES:
            with self.subTest(service=service):
                dockerfile = ROOT / service / "Dockerfile"
                self.assertTrue(dockerfile.is_file(), f"Missing {dockerfile}")
                content = dockerfile.read_text(encoding="utf-8")
                self.assertIn("FROM maven:3.9.9-eclipse-temurin-17 AS build", content)
                self.assertIn("WORKDIR /workspace", content)
                self.assertIn("COPY . .", content)
                self.assertIn(
                    f"mvn -B -ntp -pl {service} -am package -Dmaven.test.skip=true",
                    content,
                )
                self.assertIn("FROM eclipse-temurin:17-jre", content)
                self.assertIn("ENV TZ=Asia/Shanghai", content)
                self.assertIn("WORKDIR /app", content)
                self.assertIn(
                    f"COPY --from=build /workspace/{service}/target/*.jar /app/app.jar",
                    content,
                )
                self.assertIn("EXPOSE 8080", content)
                self.assertIn('ENTRYPOINT ["java", "-jar", "/app/app.jar"]', content)


if __name__ == "__main__":
    unittest.main()
