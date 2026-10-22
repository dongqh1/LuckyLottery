const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const scriptPath = path.resolve(__dirname, '../nginx/html/hmall-admin/js/admin-image-upload.js');

test('shared image upload sends the selected file to the lottery image API', async () => {
  let posted;
  class FormDataStub {
    append(name, value) { this.name = name; this.value = value; }
  }
  const context = {
    FormData: FormDataStub,
    axios: {
      async post(url, body, options) {
        posted = {url, body, options};
        return {data: {data: {url: '/uploads/prizes/2026/10/image.png'}}};
      },
    },
    window: {},
  };
  vm.runInNewContext(fs.readFileSync(scriptPath, 'utf8'), context);
  const file = {name: 'image.png'};

  const url = await context.window.AdminImageUpload.upload(file);

  assert.equal(posted.url, '/marketing/admin/prize-images');
  assert.equal(posted.body.name, 'file');
  assert.equal(posted.body.value, file);
  assert.equal(posted.options.timeout, 60000);
  assert.equal(url, '/uploads/prizes/2026/10/image.png');
});

test('shared image upload reports server failure instead of returning an empty url', async () => {
  const context = {
    FormData: class { append() {} },
    axios: { async post() { return {data: {data: {}}}; } },
    window: {},
  };
  vm.runInNewContext(fs.readFileSync(scriptPath, 'utf8'), context);

  await assert.rejects(context.window.AdminImageUpload.upload({name: 'image.png'}), /未返回图片地址/);
});
