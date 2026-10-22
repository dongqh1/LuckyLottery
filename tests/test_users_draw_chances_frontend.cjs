const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const adminRoot = path.resolve(__dirname, '../nginx/html/hmall-admin');
const htmlPath = path.join(adminRoot, 'users.html');
const scriptPath = path.join(adminRoot, 'js/user-draw-chances.js');
const options = fs.existsSync(scriptPath) ? require(scriptPath) : {};

function createApp(axios) {
  global.axios = axios;
  const app = options.data();
  Object.assign(app, options.methods);
  app.messages = [];
  app.$message = {
    success(message) { app.messages.push({type: 'success', message}); },
    error(message) { app.messages.push({type: 'error', message}); },
  };
  return app;
}

test('user management has a per-user chance action and dialog, while marketing no longer has the standalone panel', () => {
  const html = fs.readFileSync(htmlPath, 'utf8');
  const marketing = fs.readFileSync(path.join(adminRoot, 'marketing.html'), 'utf8');
  const marketingScript = fs.readFileSync(path.join(adminRoot, 'js/marketing.js'), 'utf8');

  assert.match(html, /label="抽奖机会"/);
  assert.match(html, /@click="openDrawChanceDialog\(scope\.row\)"/);
  assert.match(html, /drawChanceDialogVisible/);
  assert.match(html, /可用机会/);
  assert.match(html, /预留机会/);
  assert.match(html, /业务类型/);
  assert.match(html, /业务编号/);
  assert.match(html, /js\/user-draw-chances\.js/);
  assert.doesNotMatch(marketing, /抽奖机会管理/);
  assert.doesNotMatch(marketingScript, /searchDrawChances|adjustDrawChances|drawChanceAccount/);
});

test('chance dialog styles belong to user management and obsolete marketing styles are removed', () => {
  const html = fs.readFileSync(htmlPath, 'utf8');
  const marketingCss = fs.readFileSync(path.join(adminRoot, 'css/marketing.css'), 'utf8');
  const chanceCssPath = path.join(adminRoot, 'css/user-draw-chances.css');

  assert.match(html, /css\/user-draw-chances\.css/);
  assert.doesNotMatch(marketingCss, /\.chance-(?:query|content|balance|adjustment|form|empty)/);
  assert.ok(fs.existsSync(chanceCssPath), 'user draw chance dialog styles must exist');
  assert.match(fs.readFileSync(chanceCssPath, 'utf8'), /\.draw-chance-balances/);
});

test('opening a user row loads that user account and first ledger page on demand', async () => {
  assert.equal(typeof options.methods?.openDrawChanceDialog, 'function', 'user chance dialog behavior must be implemented');
  const calls = [];
  const app = createApp({
    get: async (url, config) => {
      calls.push({url, config});
      if (url.endsWith('/ledger')) return {data: {data: {records: [{id: 1}], total: 1}}};
      return {data: {data: {userId: 23, availableBalance: 8, reservedBalance: 2}}};
    },
  });

  await app.openDrawChanceDialog({id: 23, username: 'Jack'});

  assert.equal(app.drawChanceDialogVisible, true);
  assert.equal(app.selectedChanceUser.username, 'Jack');
  assert.deepEqual(calls.map((call) => call.url), [
    '/marketing/admin/draw-chances/users/23',
    '/marketing/admin/draw-chances/users/23/ledger',
  ]);
  assert.deepEqual(calls[1].config.params, {page: 1, size: 20});
  assert.equal(app.drawChanceAccount.availableBalance, 8);
  assert.equal(app.drawChanceAccount.reservedBalance, 2);
  assert.equal(app.drawChanceLedger[0].id, 1);
});

test('chance account and ledger requests allow the service more than the page default one second', async () => {
  const calls = [];
  const app = createApp({
    get: async (url, config) => {
      calls.push({url, config});
      return url.endsWith('/ledger')
        ? {data: {data: {records: [], total: 0}}}
        : {data: {data: {userId: 23, availableBalance: 0, reservedBalance: 0}}};
    },
  });

  await app.openDrawChanceDialog({id: 23, username: 'Jack'});

  assert.equal(calls.length, 2);
  assert.ok(calls.every(({config}) => config && config.timeout >= 10000));
});

test('changing the selected user clears the previous account before loading new data', async () => {
  assert.equal(typeof options.methods?.openDrawChanceDialog, 'function', 'user chance dialog behavior must be implemented');
  const app = createApp({
    get: async (url) => url.endsWith('/ledger')
      ? {data: {data: {records: [], total: 0}}}
      : {data: {data: {userId: 34, availableBalance: 0, reservedBalance: 0}}},
  });
  app.drawChanceAccount = {userId: 23, availableBalance: 8, reservedBalance: 2};
  app.drawChanceLedger = [{id: 1}];

  await app.openDrawChanceDialog({id: 34, username: 'Rose'});

  assert.equal(app.selectedChanceUser.id, 34);
  assert.equal(app.drawChanceAccount.userId, 34);
  assert.equal(app.drawChanceAccount.availableBalance, 0);
  assert.deepEqual(app.drawChanceLedger, []);
});

test('a late response from an older ledger page cannot overwrite the latest page', async () => {
  assert.equal(typeof options.methods?.loadDrawChanceLedger, 'function', 'ledger pagination behavior must be implemented');
  const pending = [];
  const app = createApp({get: async (_url, config) => new Promise((resolve) => pending.push({page: config.params.page, resolve}))});
  app.selectedChanceUser = {id: 23, username: 'Jack'};
  app.drawChanceLoadKey = 1;

  const pageTwo = app.loadDrawChanceLedger(2);
  const pageThree = app.loadDrawChanceLedger(3);
  pending[1].resolve({data: {data: {records: [{id: 3}], total: 60}}});
  await pageThree;
  pending[0].resolve({data: {data: {records: [{id: 2}], total: 60}}});
  await pageTwo;

  assert.equal(app.drawChanceLedgerPage, 3);
  assert.deepEqual(app.drawChanceLedger, [{id: 3}]);
});

test('a late adjustment response for the previous user does not reset the newly selected user form', async () => {
  assert.equal(typeof options.methods?.adjustDrawChances, 'function', 'chance adjustment behavior must be implemented');
  let resolveAdjustment;
  const app = createApp({
    post: async () => new Promise((resolve) => { resolveAdjustment = resolve; }),
    get: async (url) => {
      const userId = Number(url.match(/users\/(\d+)/)[1]);
      return url.endsWith('/ledger')
        ? {data: {data: {records: [], total: 0}}}
        : {data: {data: {userId, availableBalance: 0, reservedBalance: 0}}};
    },
  });
  await app.openDrawChanceDialog({id: 23, username: 'Jack'});
  Object.assign(app.chanceAdjustmentForm, {direction: 'CREDIT', amount: 3, reason: '给 Jack 补发'});
  const adjustment = app.adjustDrawChances();

  await app.openDrawChanceDialog({id: 34, username: 'Rose'});
  Object.assign(app.chanceAdjustmentForm, {direction: 'DEBIT', amount: 5, reason: 'Rose 的调整'});
  resolveAdjustment({data: {data: {}}});
  await adjustment;

  assert.equal(app.selectedChanceUser.id, 34);
  assert.deepEqual({...app.chanceAdjustmentForm}, {direction: 'DEBIT', amount: 5, reason: 'Rose 的调整'});
});

test('adjusting opportunities records an idempotent adjustment and refreshes account and ledger', async () => {
  assert.equal(typeof options.methods?.adjustDrawChances, 'function', 'chance adjustment behavior must be implemented');
  const calls = [];
  const app = createApp({
    post: async (url, body) => {
      calls.push({method: 'post', url, body});
      return {data: {data: {account: {userId: 23, availableBalance: 11, reservedBalance: 2}}}};
    },
    get: async (url) => {
      calls.push({method: 'get', url});
      return url.endsWith('/ledger')
        ? {data: {data: {records: [{id: 2}], total: 1}}}
        : {data: {data: {userId: 23, availableBalance: 11, reservedBalance: 2}}};
    },
  });
  app.selectedChanceUser = {id: 23, username: 'Jack'};
  Object.assign(app.chanceAdjustmentForm, {direction: 'CREDIT', amount: 3, reason: '联调补发'});

  await app.adjustDrawChances();

  assert.match(calls[0].body.adjustmentId, /^admin-/);
  assert.deepEqual({...calls[0].body}, {
    adjustmentId: calls[0].body.adjustmentId, direction: 'CREDIT', amount: 3, reason: '联调补发',
  });
  assert.equal(calls[0].url, '/marketing/admin/draw-chances/users/23/adjustments');
  assert.equal(calls[1].url, '/marketing/admin/draw-chances/users/23');
  assert.equal(calls[2].url, '/marketing/admin/draw-chances/users/23/ledger');
  assert.equal(app.drawChanceAccount.availableBalance, 11);
  assert.equal(app.drawChanceLedger[0].id, 2);
  assert.equal(app.messages[0].type, 'success');
});

test('a failed adjustment retry reuses its adjustment ID and shows forbidden errors', async () => {
  assert.equal(typeof options.methods?.adjustDrawChances, 'function', 'chance adjustment behavior must be implemented');
  const ids = [];
  let attempts = 0;
  const app = createApp({
    post: async (_url, body) => {
      ids.push(body.adjustmentId);
      attempts++;
      if (attempts === 1) throw {response: {status: 503, data: {message: '服务暂不可用'}}};
      return {data: {data: {account: {userId: 23, availableBalance: 1, reservedBalance: 0}}}};
    },
    get: async (url) => url.endsWith('/ledger')
      ? {data: {data: {records: [], total: 0}}}
      : {data: {data: {userId: 23, availableBalance: 1, reservedBalance: 0}}},
  });
  app.selectedChanceUser = {id: 23, username: 'Jack'};
  Object.assign(app.chanceAdjustmentForm, {direction: 'DEBIT', amount: 1, reason: '测试扣减'});

  await app.adjustDrawChances();
  await app.adjustDrawChances();

  assert.equal(ids[0], ids[1]);
  assert.ok(app.messages.some((entry) => entry.message === '服务暂不可用'));

  global.axios.get = async () => { throw {response: {status: 403, data: {message: 'Forbidden'}}}; };
  await app.openDrawChanceDialog({id: 23, username: 'Jack'});
  assert.ok(app.messages.some((entry) => entry.message === '当前账号没有抽奖管理权限'));
});
