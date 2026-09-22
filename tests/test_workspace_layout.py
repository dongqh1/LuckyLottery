from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]


class WorkspaceLayoutTest(unittest.TestCase):
    def test_copies_each_source_without_embedded_vcs_or_dependencies(self):
        expected_files = [
            ROOT / "mall-backend" / "pom.xml",
            ROOT / "mall-backend" / "hm-service" / "pom.xml",
            ROOT / "marketing-service" / "pom.xml",
            ROOT / "marketing-service" / "src" / "main" / "resources" / "redis" / "lottery" / "reserve_draw_quota.lua",
            ROOT / "mysql" / "init" / "hmall.sql",
            ROOT / "nginx" / "nginx.conf",
            ROOT / "nginx" / "html" / "hmall-portal" / "index.html",
        ]
        missing = [str(path.relative_to(ROOT)) for path in expected_files if not path.is_file()]
        self.assertEqual([], missing, f"missing copied source files: {missing}")

        forbidden = [
            ROOT / "marketing-service" / ".git",
            ROOT / "marketing-service" / ".worktrees",
            ROOT / "marketing-service" / "frontend" / "node_modules",
        ]
        present = [str(path.relative_to(ROOT)) for path in forbidden if path.exists()]
        self.assertEqual([], present, f"VCS/dependency artifacts must not be copied: {present}")

        gitignore = (ROOT / ".gitignore").read_text(encoding="utf-8")
        self.assertIn("**/target/", gitignore)
        self.assertIn("**/node_modules/", gitignore)


if __name__ == "__main__":
    unittest.main()
