const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const htmlPath = path.join(__dirname, '..', 'nginx', 'html', 'hmall-portal', 'order-detail.html');
const html = fs.readFileSync(htmlPath, 'utf8');
const inlineScript = [...html.matchAll(/<script>([\s\S]*?)<\/script>/g)][0]?.[1];

function loadOptions(axios) {
  let options;
  function Vue(config) { options = config; }
  vm.runInNewContext(inlineScript, {Vue, axios, util: {isLogin: () => true}});
  return options;
}

test('shop order detail renders live logistics status and tracking nodes', () => {
  assert.match(html, /物流轨迹/);
  assert.match(html, /shipping\.tracking/);
  assert.match(html, /shippingStatusName\(shipping\.status\)/);
});

test('loads a customer-owned cash-order shipment and unwraps the API response', async () => {
  let request;
  const options = loadOptions({
    get: async (url, config) => {
      request = {url, config};
      return {data: {shippingNo: 'SHIPPING-42', status: 'IN_TRANSIT', tracking: []}};
    },
  });
  const app = {...options.data};

  await options.methods.loadShipping.call(app, '42');

  assert.equal(request.url, '/shipping/orders/by-source');
  assert.equal(request.config.params.sourceType, 'CASH_ORDER');
  assert.equal(request.config.params.sourceId, '42');
  assert.equal(app.shipping.shippingNo, 'SHIPPING-42');
  assert.equal(app.shipping.status, 'IN_TRANSIT');
  assert.equal(app.shippingLoading, false);
});
