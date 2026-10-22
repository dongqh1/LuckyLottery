const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const html = fs.readFileSync(path.join(__dirname, '..', 'nginx', 'html', 'hmall-portal', 'benefits.html'), 'utf8');
const inlineScript = [...html.matchAll(/<script>([\s\S]*?)<\/script>/g)][0]?.[1];

function createSessionStorage() {
  const values = new Map();
  return {
    getItem: key => values.has(key) ? values.get(key) : null,
    setItem: (key, value) => values.set(key, String(value)),
    removeItem: key => values.delete(key),
  };
}

function loadOptions(axios, requestIds = ['123e4567-e89b-42d3-a456-426614174000'], sessionStorage = createSessionStorage()) {
  let options;
  function Vue(config) { options = config; }
  vm.runInNewContext(inlineScript, {
    Vue,
    axios,
    util: {isLogin: () => true, store: {get: key => key === 'user-info' ? {id: 7} : null}},
    sessionStorage,
    window: {crypto: {randomUUID: () => requestIds.shift()}},
  });
  return options;
}

test('physical lottery benefits expose a claim action and address selection', () => {
  assert.match(html, /canClaim\(item\)/);
  assert.match(html, /claimAddressId/);
  assert.match(html, /\/address\.html/);
});

test('loads addresses and claims a physical benefit through the lottery API', async () => {
  const requests = [];
  const options = loadOptions({
    get: async (url) => {
      requests.push({method: 'GET', url});
      if (url === '/addresses') return [{id: 31, contact: '李先生', isDefault: 1}];
      return {data: {records: [], total: 0}};
    },
    post: async (url, body) => {
      requests.push({method: 'POST', url, body});
      return {data: {shippingNo: 'SHIPPING-18'}};
    },
  });
  const app = {...options.data};
  Object.assign(app, options.methods);
  app.addresses = [];
  app.claimAddressId = null;
  app.claimingBenefitId = null;
  app.claimBusy = false;
  app.claimNotice = '';
  app.error = '';

  await options.methods.beginClaim.call(app, {id: 18, status: 'CLAIM_PENDING'});
  assert.equal(app.addresses.length, 1);
  assert.equal(app.claimAddressId, 31);

  await options.methods.submitClaim.call(app, {id: 18, status: 'CLAIM_PENDING'});

  const claim = requests.find(request => request.method === 'POST');
  assert.equal(claim.url, '/marketing/benefits/18/claim');
  assert.equal(claim.body.addressId, 31);
  assert.equal(claim.body.requestId, '123e4567-e89b-42d3-a456-426614174000');
  assert.match(app.claimNotice, /SHIPPING-18/);
  assert.equal(app.claimBusy, false);
});

test('retries a timed-out claim after page reload with the same request id and address', async () => {
  const claims = [];
  const sessionStorage = createSessionStorage();
  const options = loadOptions({
    get: async (url) => {
      if (url === '/addresses') return [
        {id: 31, contact: '李先生', isDefault: 1},
        {id: 32, contact: '王女士', isDefault: 0},
      ];
      return {data: {records: [], total: 0}};
    },
    post: async (url, body) => {
      claims.push(body);
      throw new Error('response timed out');
    },
  }, [
    '123e4567-e89b-42d3-a456-426614174001',
  ], sessionStorage);
  const app = {...options.data};
  Object.assign(app, options.methods);
  app.addresses = [];
  app.addressesLoaded = false;
  app.claimAddressId = '';
  app.claimBusy = false;
  app.claimError = '';
  app.claimNotice = '';

  await options.methods.beginClaim.call(app, {id: 18, status: 'CLAIM_PENDING'});
  await options.methods.submitClaim.call(app, {id: 18, status: 'CLAIM_PENDING'});
  assert.match(app.claimError, /领取失败/);
  assert.equal(app.claimAddressLocked, true);

  const retryOptions = loadOptions({
    get: async (url) => {
      if (url === '/addresses') return [
        {id: 31, contact: '李先生', isDefault: 1},
        {id: 32, contact: '王女士', isDefault: 0},
      ];
      return {data: {records: [], total: 0}};
    },
    post: async (url, body) => {
      claims.push(body);
      return {data: {shippingNo: 'SHIPPING-18'}};
    },
  }, ['123e4567-e89b-42d3-a456-426614174002'], sessionStorage);
  const retryApp = {...retryOptions.data};
  Object.assign(retryApp, retryOptions.methods);
  retryApp.addresses = [];
  retryApp.addressesLoaded = false;
  retryApp.claimAddressId = '';
  retryApp.claimBusy = false;
  retryApp.claimError = '';
  retryApp.claimNotice = '';
  await retryOptions.methods.beginClaim.call(retryApp, {id: 18, status: 'CLAIM_PENDING'});
  assert.equal(retryApp.claimAddressId, 31);
  assert.equal(retryApp.claimAddressLocked, true);
  retryApp.claimAddressId = 32;
  await retryOptions.methods.submitClaim.call(retryApp, {id: 18, status: 'CLAIM_PENDING'});

  assert.equal(claims.length, 2);
  assert.equal(claims[1].requestId, claims[0].requestId);
  assert.equal(claims[1].addressId, claims[0].addressId);
  assert.equal(claims[0].addressId, 31);
  assert.match(retryApp.claimNotice, /SHIPPING-18/);
});

test('allows choosing the shipping address before submitting the claim', async () => {
  const claims = [];
  const options = loadOptions({
    get: async (url) => {
      if (url === '/addresses') return [
        {id: 31, contact: '李先生', isDefault: 1},
        {id: 32, contact: '王女士', isDefault: 0},
      ];
      return {data: {records: [], total: 0}};
    },
    post: async (url, body) => {
      claims.push(body);
      return {data: {shippingNo: 'SHIPPING-18'}};
    },
  }, [
    '123e4567-e89b-42d3-a456-426614174011',
    '123e4567-e89b-42d3-a456-426614174012',
  ]);
  const app = {...options.data};
  Object.assign(app, options.methods);
  app.addresses = [];
  app.addressesLoaded = false;
  app.claimAddressId = '';
  app.claimBusy = false;
  app.claimError = '';
  app.claimNotice = '';

  await options.methods.beginClaim.call(app, {id: 18, status: 'CLAIM_PENDING'});
  app.claimAddressId = 32;
  options.methods.changeClaimAddress.call(app);
  await options.methods.submitClaim.call(app, {id: 18, status: 'CLAIM_PENDING'});

  assert.equal(claims.length, 1);
  assert.equal(claims[0].addressId, 32);
  assert.equal(claims[0].requestId, '123e4567-e89b-42d3-a456-426614174011');
});

test('releases a saved claim attempt only after logistics confirms no shipment exists', async () => {
  const sessionStorage = createSessionStorage();
  sessionStorage.setItem('hmall:shipping-claim:7:18', JSON.stringify({
    requestId: '123e4567-e89b-42d3-a456-426614174021',
    addressId: 31,
  }));
  const claims = [];
  const lookups = [];
  const options = loadOptions({
    get: async (url) => {
      if (url === '/addresses') return [{id: 32, contact: '王女士', isDefault: 1}];
      lookups.push(url);
      const missing = new Error('shipping order not found');
      missing.response = {status: 404, data: {code: 55005, msg: '发货单不存在'}};
      throw missing;
    },
    post: async (url, body) => {
      claims.push(body);
      return {data: {shippingNo: 'SHIPPING-18'}};
    },
  }, ['123e4567-e89b-42d3-a456-426614174022'], sessionStorage);
  const app = {...options.data};
  Object.assign(app, options.methods);
  app.addresses = [];
  app.addressesLoaded = false;
  app.claimAddressId = '';
  app.claimBusy = false;
  app.claimError = '';
  app.claimNotice = '';

  await options.methods.beginClaim.call(app, {id: 18, status: 'CLAIM_PENDING'});

  assert.deepEqual(lookups, ['/shipping/orders/by-source']);
  assert.equal(app.claimAddressLocked, false);
  assert.equal(app.claimAddressId, 32);
  assert.match(app.claimError, /之前的领取未创建物流单/);
  assert.equal(sessionStorage.getItem('hmall:shipping-claim:7:18'), null);

  await options.methods.submitClaim.call(app, {id: 18, status: 'CLAIM_PENDING'});
  assert.equal(claims.length, 1);
  assert.equal(claims[0].addressId, 32);
  assert.equal(claims[0].requestId, '123e4567-e89b-42d3-a456-426614174022');
});

test('loads the signed-in user shipping events for a claimed physical benefit', async () => {
  let request;
  const options = loadOptions({
    get: async (url) => {
      request = url;
      return {data: [{eventType: 'IN_TRANSIT', description: '包裹运输中'}]};
    },
  });
  const app = {...options.data};
  Object.assign(app, options.methods);
  app.trackingShippingNo = null;
  app.trackingEvents = [];
  app.trackingLoading = false;
  app.trackingError = '';

  await options.methods.loadTracking.call(app, {shippingNo: 'SHIPPING-18'});

  assert.equal(request, '/shipping/orders/SHIPPING-18/tracking');
  assert.equal(app.trackingEvents[0].description, '包裹运输中');
  assert.equal(app.trackingLoading, false);
});
