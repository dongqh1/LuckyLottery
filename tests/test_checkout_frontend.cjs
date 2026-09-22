const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const portal = path.resolve(__dirname, '../nginx/html/hmall-portal');
function cashier(axios) {
  let options;
  const context = {
    Vue: function(config) { options = config; }, axios,
    util: { store: {get() {}, set() {}}, getUrlParam() { return '42'; } },
    location: {}, alert() {}, confirm() { return true; }, console,
    setInterval, clearInterval, setTimeout, clearTimeout
  };
  const html = fs.readFileSync(path.join(portal, 'pay.html'), 'utf8');
  const code = [...html.matchAll(/<script\b[^>]*>([\s\S]*?)<\/script>/gi)].map(m => m[1]).join('\n');
  vm.runInNewContext(code, context);
  const app = options.data();
  Object.entries(options.methods).forEach(([name, fn]) => { app[name] = fn.bind(app); });
  app.order = {id:'42', status:1, totalFee:1300, couponId:null, paymentType:3};
  return {app, context};
}

test('cashier loads applicable coupons from owned order items, never session cart', async () => {
  const {app} = cashier({
    get: async url => { assert.equal(url, '/orders/42/checkout-items'); return [{itemId:'561178',num:1}]; },
    post: async (url, body) => {
      assert.equal(url, '/marketing/coupons/checkout/applicable');
      assert.equal(body.items[0].itemId, '561178');
      assert.equal(body.items[0].quantity, 1);
      return {data:[{userCouponId:'2096098706886033409',discountCent:150}]};
    }
  });
  await app.loadCheckoutCoupons();
  assert.equal(app.applicableCoupons[0].userCouponId, '2096098706886033409');
  assert.equal(app.couponLoading, false);
});

test('apply coupon preserves exact ID and trusts server price before reissuing payment', async () => {
  const {app} = cashier({put: async (url, body) => {
    assert.equal(url, '/orders/42/coupon');
    assert.equal(body.couponId, '2096098706886033409');
    assert.deepEqual(Object.keys(body), ['couponId']);
    return {id:'42',status:1,couponId:body.couponId,totalFee:1150,couponDiscountFee:150,paymentType:3};
  }});
  app.selectedCouponId = '2096098706886033409';
  app.payOrderNo = 'old-payment';
  let issued = false;
  app.createPayOrder = async () => {
    assert.equal(app.order.totalFee, 1150);
    assert.equal(app.payOrderNo, '');
    issued = true;
  };
  await app.applyCoupon();
  assert.equal(issued, true);
  assert.equal(app.order.couponId, '2096098706886033409');
});

test('failed coupon application never enables payment with an old payment number', async () => {
  const {app} = cashier({put: async () => { throw new Error('offline'); }});
  app.selectedCouponId = '2096098706886033409';
  app.payOrderNo = 'old-payment';
  await app.applyCoupon();
  assert.equal(app.payOrderNo, '');
  assert.ok(app.couponError);
});

test('payment is blocked while a selected coupon has not been applied', async () => {
  let paid = false;
  const {app} = cashier({post:async () => {paid = true;}});
  app.selectedCouponId = '2096098706886033409';
  app.payOrderNo = 'old-payment';
  await app.payByBalance();
  assert.equal(paid, false);
});

test('late response from the old payment quote cannot overwrite the refreshed payment', async () => {
  let resolveOld;
  let calls = 0;
  const {app} = cashier({post: () => {
    calls++;
    return calls === 1 ? new Promise(resolve => {resolveOld = resolve;}) : Promise.resolve('new-payment');
  }});
  const pending = app.createPayOrder('42');
  await app.createPayOrder('42');
  resolveOld('old-payment');
  await pending;
  assert.equal(app.payOrderNo, 'new-payment');
});

test('terminal orders cannot initiate another payment or coupon change', async () => {
  let writes = 0;
  const {app} = cashier({post:async () => {writes++;},put:async () => {writes++;}});
  app.order.status = 2;
  app.selectedCouponId = '2096098706886033409';
  await app.applyCoupon();
  await app.createPayOrder('42');
  await app.payByBalance();
  assert.equal(writes, 0);
});

test('coupon discovery outage still permits original-price balance payment', async () => {
  const posted = [];
  const order = {id:'42',status:1,totalFee:1300,couponId:null,paymentType:3,createTime:new Date().toISOString()};
  const {app} = cashier({
    get: async url => {
      if (url === '/orders/42') return order;
      if (url === '/orders/42/checkout-items') throw new Error('marketing unavailable');
      throw new Error('unexpected '+url);
    },
    post: async url => { posted.push(url); return url === '/pay-orders' ? 'original-payment' : null; }
  });
  await app.refreshCheckout();
  assert.ok(app.couponQueryError);
  assert.equal(app.couponError, '');
  assert.equal(app.payOrderNo, 'original-payment');
  app.password = 'not-a-real-secret';
  await app.payByBalance();
  assert.deepEqual(posted, ['/pay-orders', '/pay-orders/original-payment']);
  clearInterval(app.countdownTimer);
});

test('checkout refresh is single-flight so stale order cannot overwrite coupon state', async () => {
  let resolveOrder;
  let orderReads = 0;
  const {app} = cashier({
    get: url => {
      if (url === '/orders/42') {
        orderReads++;
        return new Promise(resolve => { resolveOrder = resolve; });
      }
      return Promise.resolve([]);
    },
    post: async url => url === '/pay-orders' ? 'payment' : {data:[]}
  });
  const first = app.refreshCheckout();
  const second = app.refreshCheckout();
  assert.equal(orderReads, 1);
  resolveOrder({id:'42',status:2,totalFee:1150,couponId:'9',paymentType:3,createTime:new Date().toISOString()});
  await Promise.all([first, second]);
  assert.equal(app.order.couponId, '9');
});
