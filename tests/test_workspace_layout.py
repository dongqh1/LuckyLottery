from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]


class WorkspaceLayoutTest(unittest.TestCase):
    def test_active_sources_exist_and_removed_copies_are_absent(self):
        expected_files = [
            ROOT / "mall-backend" / "pom.xml",
            ROOT / "mall-backend" / "hm-service" / "pom.xml",
            ROOT / "coupon-service" / "pom.xml",
            ROOT / "coupon-service" / "src" / "main" / "java" / "com" / "dongqh" / "coupon" / "CouponServiceApplication.java",
            ROOT / "mysql" / "init" / "hmall.sql",
            ROOT / "nginx" / "nginx.conf",
            ROOT / "nginx" / "html" / "hmall-portal" / "index.html",
        ]
        missing = [str(path.relative_to(ROOT)) for path in expected_files if not path.is_file()]
        self.assertEqual([], missing, f"missing copied source files: {missing}")

        removed = [ROOT / "marketing-service", ROOT / "hmall-nginx"]
        present = [str(path.relative_to(ROOT)) for path in removed if path.exists()]
        self.assertEqual([], present, f"obsolete duplicate directories remain: {present}")

        gitignore = (ROOT / ".gitignore").read_text(encoding="utf-8")
        self.assertIn("**/target/", gitignore)
        self.assertIn("**/node_modules/", gitignore)


if __name__ == "__main__":
    unittest.main()
