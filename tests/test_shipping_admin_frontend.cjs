const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const htmlPath = path.join(__dirname, '..', 'nginx', 'html', 'hmall-admin', 'shipping-admin.html');
const options = require('../nginx/html/hmall-admin/js/shipping-admin.js');

function createApp(axios) {
  global.sessionStorage = {
    values: new Map([['token', 'test-token']]),
    getItem(key) { return this.values.has(key) ? this.values.get(key) : null; },
    setItem(key, value) { this.values.set(key, value); },
    removeItem(key) { this.values.delete(key); },
  };
  global.axios = axios;
  const app = options.data();
  Object.assign(app, options.methods);
  app.$message = { error() {}, warning() {}, success() {} };
  return app;
}

test('logistics page includes admin entry, source filters, masked receiver details and tracking controls', () => {
  const html = fs.readFileSync(htmlPath, 'utf8');
  assert.match(html, /href="shipping-admin\.html" aria-current="page"/);
  assert.match(html, /value="CASH_ORDER"/);
  assert.match(html, /value="LOTTERY_BENEFIT"/);
  assert.match(html, /value="POINTS_REDEMPTION"/);
  assert.match(html, /scope\.row\.receiverMasked/);
  assert.match(html, /@click="addTrackingEvent"/);
  assert.match(html, /js\/shipping-admin\.js/);
});

test('loads the requested shipping filters and updates the page records', async () => {
  let request;
  const app = createApp({
    get: async (url, config) => {
      request = {url, config};
      return {data: {data: {records: [{shippingNo: 'SHIPPING-1'}], total: 1}}};
    },
  });
  app.page = 3;
  app.filters = {sourceType: 'LOTTERY_BENEFIT', status: 'READY', sourceId: '42', targetUserId: '7', waybillNo: ''};

  await app.loadOrders();

  assert.equal(request.url, '/admin/shipping/orders');
  assert.deepEqual(request.config.params, {
    page: 3, size: 20, sourceType: 'LOTTERY_BENEFIT', status: 'READY', sourceId: '42', targetUserId: '7',
  });
  assert.equal(app.orders[0].shippingNo, 'SHIPPING-1');
  assert.equal(app.total, 1);
});

test('admin can replay durable failed paid-order logistics messages', async () => {
  let request;
  const app = {...options.data()};
  Object.assign(app, options.methods);
  app.$message = {success: (message) => { app.successMessage = message; }, error: assert.fail};
  const originalPost = global.axios;
  global.axios = {post: async (url, body, config) => {
    request = {url, body, config};
    return {data: {data: 3}};
  }};
  try {
    await options.methods.replayPaidEvents.call(app);
  } finally {
    global.axios = originalPost;
  }
  assert.equal(request.url, '/admin/shipping/operations/replay-paid-events');
  assert.equal(request.config.params.limit, 100);
  assert.match(app.successMessage, /3 条/);
  assert.equal(app.replayBusy, false);
});

test('saves carrier details and refreshes the shipping list', async () => {
  const calls = [];
  const app = createApp({
    put: async (url, body) => {
      calls.push({method: 'put', url, body});
      return {data: {data: {shippingNo: 'SHIPPING-2', waybillNo: body.waybillNo}}};
    },
    get: async (url) => {
      calls.push({method: 'get', url});
      return {data: {data: {records: [], total: 0}}};
    },
  });
  app.selectedOrder = {shippingNo: 'SHIPPING-2', sourceType: 'CASH_ORDER'};
  app.logisticsForm = {carrierCode: 'YTO', carrierName: '圆通速递', waybillNo: '12345678901'};

  await app.saveLogistics();

  assert.deepEqual(calls[0], {
    method: 'put',
    url: '/admin/shipping/orders/SHIPPING-2/logistics',
    body: {carrierCode: 'YTO', carrierName: '圆通速递', waybillNo: '12345678901'},
  });
  assert.equal(calls[1].url, '/admin/shipping/orders');
  assert.equal(app.selectedOrder.waybillNo, '12345678901');
});

test('adds a simulated tracking event with an optional timestamp and reloads order details', async () => {
  const calls = [];
  const app = createApp({
    post: async (url, body) => { calls.push({method: 'post', url, body}); return {data: {data: {}}}; },
    get: async (url, config) => {
      calls.push({method: 'get', url, config});
      if (url.endsWith('/tracking')) return {data: {data: [{eventType: 'IN_TRANSIT'}]}};
      if (url.includes('/SHIPPING-3')) return {data: {data: {shippingNo: 'SHIPPING-3', status: 'IN_TRANSIT', waybillNo: 'WB-3'}}};
      return {data: {data: {records: [], total: 0}}};
    },
  });
  app.detailsVisible = true;
  app.selectedOrder = {shippingNo: 'SHIPPING-3', waybillNo: 'WB-3'};
  app.trackingForm = {eventType: 'IN_TRANSIT', locationSummary: '杭州转运中心', description: '包裹已到达转运中心', eventTime: ''};

  await app.addTrackingEvent();

  assert.deepEqual(calls[0], {
    method: 'post',
    url: '/admin/shipping/orders/SHIPPING-3/tracking',
    body: {eventType: 'IN_TRANSIT', locationSummary: '杭州转运中心', description: '包裹已到达转运中心', eventTime: null},
  });
  assert.equal(app.selectedOrder.status, 'IN_TRANSIT');
  assert.equal(app.tracking[0].eventType, 'IN_TRANSIT');
  assert.deepEqual(app.trackingForm, {eventType: 'OUT_FOR_DELIVERY', locationSummary: '', description: '', eventTime: ''});
});

test('defaults a tracking-in-transit order to the next valid simulated event', async () => {
  const app = createApp({
    get: async (url) => {
      if (url.endsWith('/tracking')) {
        return {data: {data: [
          {eventType: 'PICKED_UP'},
          {eventType: 'IN_TRANSIT'},
        ]}};
      }
      return {data: {data: {shippingNo: 'SHIPPING-4', status: 'IN_TRANSIT', waybillNo: 'WB-4'}}};
    },
  });

  await app.openDetails({shippingNo: 'SHIPPING-4', status: 'IN_TRANSIT', waybillNo: 'WB-4'});

  assert.equal(app.trackingForm.eventType, 'OUT_FOR_DELIVERY');
});
