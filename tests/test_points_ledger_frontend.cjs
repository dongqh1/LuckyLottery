const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const pagePath = path.resolve(__dirname, '../nginx/html/hmall-portal/points.html');
const html = fs.readFileSync(pagePath, 'utf8');

function mount(axios) {
  let options;
  const script = [...html.matchAll(/<script\b[^>]*>([\s\S]*?)<\/script>/gi)]
    .map(match => match[1]).join('\n');
  vm.runInNewContext(script, {
    Vue: function (config) { options = config; },
    axios,
    util: { isLogin() { return true; } },
    location: {},
    console,
  });
  const app = options.data;
  for (const [name, method] of Object.entries(options.methods)) app[name] = method.bind(app);
  return app;
}

test('points page displays the complete ledger separately from redemption orders', () => {
  assert.match(html, /积分流水/);
  assert.match(html, /变动后余额/);
  assert.match(html, /流水时间/);
  assert.match(html, /我的兑换/);
  assert.match(html, /查看物流/);
  assert.match(html, /shippingEvents/);
});

test('points redemption can show the current logistics tracking nodes', async () => {
  let request;
  const app = mount({get: async (url) => {
    request = url;
    return {data: [{eventType: 'DELIVERED', description: '已签收'}]};
  }});
  app.shippingEvents = [];
  app.shippingError = '';
  app.shippingLoading = false;
  app.trackingRedemptionId = null;

  await app.viewShipping({id: 7, shippingNo: 'SHIPPING-7'});

  assert.equal(request, '/shipping/orders/SHIPPING-7/tracking');
  assert.equal(app.shippingEvents[0].description, '已签收');
  assert.equal(app.trackingRedemptionId, 7);
  assert.equal(app.shippingLoading, false);
});

test('ledger loads the signed-in user’s paged records', async () => {
  const calls = [];
  const app = mount({ get: async (url, config) => {
    calls.push({ url, config });
    return { data: { records: [{ id: 7, direction: 'CREDIT', amount: 20, balanceAfter: 80 }],
      total: 11, page: 2, size: 10, pages: 2 } };
  } });
  await app.loadLedgers(2);
  assert.equal(calls[0].url, '/marketing/points/ledgers');
  assert.equal(calls[0].config.params.page, 2);
  assert.equal(calls[0].config.params.size, 10);
  assert.equal(app.ledgers.records[0].balanceAfter, 80);
  assert.equal(app.ledgers.pages, 2);
});

test('switching to income resets the ledger to page one', async () => {
  const calls = [];
  const app = mount({ get: async (url, config) => {
    calls.push(config.params);
    return { data: { records: [], total: 0, page: 1, size: 10, pages: 0 } };
  } });
  app.ledgers.page = 3;
  await app.setLedgerDirection('CREDIT');
  assert.equal(calls[0].page, 1);
  assert.equal(calls[0].direction, 'CREDIT');
});

test('ledger failure leaves the balance and offers available', async () => {
  const app = mount({ get: async () => { throw new Error('unavailable'); } });
  app.account.balance = 50;
  app.offers = [{ id: 1 }];
  await app.loadLedgers(1);
  assert.equal(app.account.balance, 50);
  assert.equal(app.offers.length, 1);
  assert.ok(app.ledgerError);
});
