(function (global) {
  function unwrap(response) {
    var body = response && response.data;
    return body && Object.prototype.hasOwnProperty.call(body, 'data') ? body.data : body;
  }

  global.AdminImageUpload = {
    upload: async function (file) {
      if (!file) throw new Error('请选择图片文件');
      var payload = new FormData();
      payload.append('file', file);
      var response = await axios.post('/marketing/admin/prize-images', payload, {timeout: 60000});
      var result = unwrap(response);
      if (!result || !result.url) throw new Error('图片上传接口未返回图片地址');
      return result.url;
    }
  };
})(window);
