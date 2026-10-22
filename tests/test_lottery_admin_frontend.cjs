const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const adminRoot = path.resolve(__dirname, '../nginx/html/hmall-admin');

function mount(handlers) {
  let options;
  const axios = {
    defaults: {},
    interceptors: { request: { use() {} } },
    ...handlers,
  };
  const context = {
    axios,
    window: { AdminImageUpload: { upload: async () => '/uploads/prizes/test.png' } },
    Vue: function (config) { options = config; },
    sessionStorage: { getItem() { return 'test-token'; }, setItem() {}, clear() {} },
    location: { reload() {} },
    console,
  };
  const script = fs.readFileSync(path.join(adminRoot, 'js/marketing.js'), 'utf8');
  vm.runInNewContext(script, context);
  const app = options.data();
  app._window = context.window;
  for (const [name, getter] of Object.entries(options.computed || {})) {
    Object.defineProperty(app, name, {get: getter.bind(app)});
  }
  for (const [name, method] of Object.entries(options.methods)) app[name] = method.bind(app);
  app.$message = { success() {}, error(message) { throw new Error(message); } };
  return app;
}

function mountItems() {
  let options;
  const context = {
    axios: {defaults: {}, interceptors: {request: {use() {}}}},
    Vue: function (config) { options = config; },
    sessionStorage: {getItem() { return 'test-token'; }},
  };
  const html = fs.readFileSync(path.join(adminRoot, 'items.html'), 'utf8');
  const inlineScripts = [...html.matchAll(/<script>([\s\S]*?)<\/script>/g)];
  vm.runInNewContext(inlineScripts.at(-1)[1], context);
  const app = options.data;
  for (const [name, method] of Object.entries(options.methods)) app[name] = method.bind(app);
  return app;
}

test('activity creation omits the removed daily limit', async () => {
  const html = fs.readFileSync(path.join(adminRoot, 'marketing.html'), 'utf8');
  assert.ok(!html.includes('每日次数'));
  let created;
  const app = mount({
    post: async (url, body) => { assert.equal(url, '/marketing/admin/activities'); created = body; return {data: {data: {id: 1}}}; },
    get: async () => ({data: {data: {records: [], total: 0}}}),
  });
  Object.assign(app.activityForm, {
    activityName: '秋季抽奖', description: '活动',
    startTime: '2026-10-18T00:00:00', endTime: '2026-10-19T00:00:00', noWinWeight: 10,
  });
  await app.createActivity();
  assert.equal(created.activityName, '秋季抽奖');
  assert.equal(Object.hasOwn(created, 'dailyLimit'), false);
});

test('the prize page replaces reward ID linking with one prize form', async () => {
  const html = fs.readFileSync(path.join(adminRoot, 'marketing.html'), 'utf8');
  assert.ok(!html.includes('创建奖励'));
  assert.ok(!html.includes('奖励 ID'));
  assert.ok(!html.includes('奖励配置 JSON'));
  assert.ok(!html.includes('/marketing/admin/reward-definitions'));
  assert.ok(!html.includes('抽奖机会管理'));
  assert.ok(!html.includes('label="图片地址"'));
  assert.ok(html.includes('type="file"'));
  assert.ok(html.includes('输入商品关键词搜索'));
  assert.ok(html.includes('会员等级'));
  assert.ok(html.includes('会员天数'));
});

test('searching products uses the e-commerce keyword search API', async () => {
  let request;
  const app = mount({
    get: async (url, config) => {
      request = {url, params: config.params};
      return {data: {list: [{id: 42, name: '无线耳机', price: 12900, stock: 8}], total: 1}};
    },
  });

  await app.searchProducts('无线耳机');

  assert.equal(request.url, '/search/list');
  assert.equal(request.params.key, '无线耳机');
  assert.equal(app.productOptions[0].id, 42);
  assert.equal(app.productOptionLabel(app.productOptions[0]), '无线耳机 · ¥129.00');
});

test('prize image link opens the local file picker', () => {
  const html = fs.readFileSync(path.join(adminRoot, 'marketing.html'), 'utf8');
  assert.ok(html.includes('点击选择本地图片'), 'the local image picker link should be visible');
  assert.ok(html.includes('@click.prevent="choosePrizeImage"'), 'the link should open the local picker');
  assert.ok(/type="file"[^>]*class="admin-image-upload__input"/.test(html));

  const app = mount();
  let clicks = 0;
  app.$refs = {prizeImageFile: {click() { clicks += 1; }}};
  app.choosePrizeImage();
  assert.equal(clicks, 1);
});

test('coupon templates can be searched by keyword for prize selection', async () => {
  let request;
  const app = mount({
    get: async (url, config) => {
      request = {url, params: config.params};
      return {data: {data: [{
        id: 19, templateCode: 'WELCOME-10', templateName: '新客满减券',
        couponType: 'THRESHOLD', thresholdCent: 10000, discountCent: 2000,
      }]}};
    },
  });

  await app.searchCoupons('新客');

  assert.equal(request.url, '/marketing/admin/coupon-templates');
  assert.equal(request.params.keyword, '新客');
  assert.equal(app.couponOptions[0].id, 19);
  assert.equal(app.couponOptionLabel(app.couponOptions[0]), '新客满减券 · 满 ¥100.00 减 ¥20.00');
});

test('opening the prize coupon selector loads existing enabled templates', async () => {
  let request;
  const app = mount({
    get: async (url, config) => {
      request = {url, params: config.params};
      return {data: {data: [{id: 23, templateCode: 'SUMMER-5', templateName: '夏日券',
        couponType: 'NO_THRESHOLD', thresholdCent: 0, discountCent: 500}]}};
    },
  });
  app.prizeForm.rewardType = 'COUPON';

  await app.loadCouponsOnOpen(true);

  assert.equal(request.url, '/marketing/admin/coupon-templates');
  assert.equal(request.params.size, 50);
  assert.equal(request.params.keyword, undefined);
  assert.equal(app.couponOptions[0].templateName, '夏日券');
});

test('reward pickers render product price and coupon discount in the dropdown', () => {
  const html = fs.readFileSync(path.join(adminRoot, 'marketing.html'), 'utf8');
  assert.ok(html.includes('productOptionLabel(item)'));
  assert.ok(html.includes('couponOptionLabel(coupon)'));
  assert.ok(html.includes('输入优惠券名称或编码搜索'));
  assert.doesNotMatch(html, /优惠券模板 ID/);

  const app = mount();
  assert.equal(app.formatCents(1234), '¥12.34');
  assert.equal(app.formatCouponValue({couponType: 'NO_THRESHOLD', discountCent: 500}), '¥5.00 无门槛');
});

test('creating a prize sends display and fulfillment details in one request', async () => {
  let sent;
  const app = mount({
    post: async (url, body) => {
      assert.equal(url, '/marketing/admin/prizes');
      sent = body;
      return {data: {data: {id: 48}}};
    },
    get: async () => ({data: {data: {records: [], total: 0}}}),
  });
  Object.assign(app.prizeForm, {
    prizeName: '新会员礼', rewardType: 'MEMBERSHIP', prizeLevel: 'THIRD',
    targetId: null, quantity: 1, membershipLevel: 'GOLD', durationDays: 14,
    membershipProductCode: 'LOTTERY-MEMBER-TEST', configSnapshot: '{"days":30}',
    imageUrl: ' /uploads/prizes/2026/10/prize.png ', description: ' 会员奖励 ', stackable: false,
  });
  await app.createPrize();
  assert.deepEqual(JSON.parse(JSON.stringify(sent)), {
    prizeName: '新会员礼', rewardType: 'MEMBERSHIP', prizeLevel: 'THIRD',
    targetId: null, quantity: 1,
    membershipLevel: 'GOLD', durationDays: 14, membershipProductCode: 'LOTTERY-MEMBER-TEST',
    imageUrl: '/uploads/prizes/2026/10/prize.png', description: '会员奖励', stackable: false,
  });
  assert.equal(Object.hasOwn(sent, 'configSnapshot'), false);
  assert.equal(Object.hasOwn(sent, 'rewardDefinitionId'), false);
  assert.equal(Object.hasOwn(sent, 'prizeType'), false);
  assert.equal(app.poolForm.prizeId, 48);
});

test('selecting a prize image uploads the file and stores the returned path', async () => {
  let uploadedFile;
  const contextUpload = async function (file) {
    uploadedFile = file;
    return '/uploads/prizes/test.png';
  };
  const app = mount({
    post: async () => ({data: {data: {id: 48}}}),
    get: async () => ({data: {data: {records: [], total: 0}}}),
  });
  app._context = contextUpload;
  app.prizeForm.imageUrl = '';
  app.$refs = {prizeImageFile: {value: ''}};
  app.$message = {success() {}, error(message) { throw new Error(message); }};
  const file = {name: 'gift.png'};
  app._window.AdminImageUpload.upload = app._context;
  await app.uploadPrizeImage({target: {files: [file], value: 'gift.png'}});
  assert.equal(uploadedFile, file);
  assert.equal(app.prizeForm.imageUrl, '/uploads/prizes/test.png');
  assert.equal(app.prizeImageFileName, 'gift.png');
});

test('every editable admin image field uses the file chooser pattern', () => {
  const itemHtml = fs.readFileSync(path.join(adminRoot, 'items.html'), 'utf8');
  assert.ok(!itemHtml.includes('v-model="item.image"'));
  assert.ok(itemHtml.includes('type="file"'));
  assert.ok(itemHtml.includes('AdminImageUpload'));
  assert.ok(itemHtml.includes('点击选择本地图片'));
  assert.ok(itemHtml.includes('@click.prevent="chooseItemImage"'));
  assert.ok(itemHtml.includes('admin-image-upload.css'));
});

test('product image link opens the local file picker', () => {
  const app = mountItems();
  let clicks = 0;
  app.$refs = {itemImageFile: {click() { clicks += 1; }}};
  app.chooseItemImage();
  assert.equal(clicks, 1);
});

test('adding a prize sends weight, stock, and display order to the chosen activity', async () => {
  const app = mount({
    post: async (url, body) => {
      assert.equal(url, '/marketing/admin/activities/7/prizes');
      assert.equal(body.prizeId, 48);
      assert.equal(body.weight, 20);
      assert.equal(body.totalStock, 100);
      assert.equal(body.sortOrder, 1);
      return {data: {data: {id: 3}}};
    },
    get: async () => ({data: {data: []}}),
  });
  app.selectedActivity = {id: 7, activityName: '秋季抽奖'};
  Object.assign(app.poolForm, {prizeId: 48, weight: 20, totalStock: 100, sortOrder: 1});
  await app.addPrizeToActivity();
});
