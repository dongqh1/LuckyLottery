const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const root = path.resolve(__dirname, '../nginx/html/hmall-admin');
const html = fs.readFileSync(path.join(root, 'benefits-admin.html'), 'utf8');

function mount(handlers = {}) {
  let options;
  const axios = {
    defaults: {},
    interceptors: {request: {use() {}}},
    get: async () => ({data: {data: []}}),
    post: async () => ({data: {data: {id: 1}}}),
    ...handlers,
  };
  const context = {
    axios,
    Vue: function (config) { options = config; },
    sessionStorage: {getItem() { return 'test-token'; }, setItem() {}, removeItem() {}},
    location: {reload() {}},
    console,
  };
  vm.runInNewContext(fs.readFileSync(path.join(root, 'js/benefits-admin.js'), 'utf8'), context);
  const app = options.data();
  for (const [name, method] of Object.entries(options.methods)) app[name] = method.bind(app);
  app.$message = {success() {}, error(message) { throw new Error(message); }};
  return app;
}

test('coupon scope and points offer use the same searchable mall-product dropdown', () => {
  assert.doesNotMatch(html, /el-input-number v-model="couponForm\.hmallItemId"/);
  assert.doesNotMatch(html, /el-input-number v-model="pointsOfferForm\.hmallItemId"/);
  assert.match(html, /:remote-method="searchCouponItems"/);
  assert.match(html, /:remote-method="searchPointsItems"/);
  assert.equal((html.match(/placeholder="输入商品名称关键词搜索"/g) || []).length, 2);
  assert.match(html, /productOptionLabel\(item\)/);
  assert.match(html, /formatCents\(item\.price\)/);
});

test('coupon scope product search uses the mall keyword search API and keeps item details', async () => {
  let request;
  const app = mount({
    get: async (url, config) => {
      request = {url, params: config.params};
      return {data: {list: [{id: 42, name: '无线耳机', price: 12900, stock: 8}], total: 1}};
    },
  });

  await app.searchCouponItems('无线耳机');

  assert.equal(request.url, '/search/list');
  assert.equal(request.params.key, '无线耳机');
  assert.equal(app.couponItemOptions[0].id, 42);
  assert.equal(app.productOptionLabel(app.couponItemOptions[0]), '无线耳机 · ¥129.00');
});

test('coupon template creation submits the selected mall product ID', async () => {
  let sent;
  const app = mount({
    post: async (url, body) => { sent = {url, body}; return {data: {data: {id: 9}}}; },
  });
  Object.assign(app.couponForm, {
    templateCode: 'ITEM-COUPON', templateName: '耳机券', couponType: 'THRESHOLD',
    thresholdCent: 10000, discountCent: 2000, scopeType: 'ITEM', hmallItemId: 42,
    validFrom: '2026-10-01T00:00:00', validUntil: '2026-11-01T00:00:00',
    perUserLimit: 1, stackableWithMembership: true,
  });

  await app.createCouponTemplate();

  assert.equal(sent.url, '/marketing/admin/coupon-templates');
  assert.equal(sent.body.hmallItemId, 42);
});

test('points redemption product search uses the mall keyword search API', async () => {
  let request;
  const app = mount({
    get: async (url, config) => {
      request = {url, params: config.params};
      return {data: {list: [{id: 75, name: '保温杯', price: 6900, stock: 24}], total: 1}};
    },
  });

  await app.searchPointsItems('保温杯');

  assert.equal(request.url, '/search/list');
  assert.equal(request.params.key, '保温杯');
  assert.equal(app.pointsItemOptions[0].id, 75);
});

test('points redemption creation submits the selected mall product ID', async () => {
  let sent;
  const app = mount({
    post: async (url, body) => { sent = {url, body}; return {data: {data: {id: 7}}}; },
  });
  app.pointsOfferForm.hmallItemId = 75;
  app.pointsOfferForm.unitPoints = 800;
  app.pointsOfferForm.availableStock = 20;

  await app.createPointsOffer();

  assert.equal(sent.url, '/marketing/admin/points/offers');
  assert.equal(sent.body.hmallItemId, 75);
});

