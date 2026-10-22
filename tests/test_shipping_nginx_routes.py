from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[1]


class ShippingNginxRouteContractTest(unittest.TestCase):
    def test_admin_root_redirects_to_marketing_console(self):
        config = (ROOT / "nginx" / "nginx.conf").read_text(encoding="utf-8")
        servers = re.findall(r"^    server\s*\{(.*?)(?=^    \})", config, re.M | re.S)
        admin_server = next(server for server in servers if "listen 18081;" in server)
        self.assertIn("absolute_redirect off;", admin_server)
        self.assertRegex(admin_server, r"location\s*=\s*/\s*\{\s*return\s+302\s+/marketing\.html;\s*\}")

    def test_shipping_paths_keep_api_prefix_for_gateway_in_both_sites(self):
        config = (ROOT / "nginx" / "nginx.conf").read_text(encoding="utf-8")
        servers = re.findall(r"^    server\s*\{(.*?)(?=^    \})", config, re.M | re.S)
        self.assertEqual(2, len(servers))
        for server in servers:
            match = re.search(r"location\s+\^~\s+/api/\s*\{(.*?)\n\s*\}", server, re.S)
            self.assertIsNotNone(match, "missing Nginx API proxy")
            self.assertIn(r"rewrite ^/api/(?!admin/shipping/|shipping/)(.*)$ /$1 break;", match.group(1))
            self.assertIn("proxy_pass http://$gateway_backend;", match.group(1))

    def test_logistics_admin_allowlist_is_configurable_in_compose(self):
        compose = (ROOT / "compose.yaml").read_text(encoding="utf-8")
        logistics = re.search(r"^  logistics-service:\n(.*?)(?=^  [a-zA-Z0-9-]+:|^volumes:)", compose, re.M | re.S)
        self.assertIsNotNone(logistics)
        self.assertIn("LOGISTICS_ADMIN_USER_IDS: ${LOGISTICS_ADMIN_USER_IDS:-1}", logistics.group(1))


if __name__ == "__main__":
    unittest.main()
