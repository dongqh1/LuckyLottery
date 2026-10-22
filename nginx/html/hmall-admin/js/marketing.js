axios.defaults.baseURL = '/api';
axios.interceptors.request.use(function (config) {
  var token = sessionStorage.getItem('token');
  if (token) config.headers.authorization = token;
  return config;
});

new Vue({
  el: '#app',
  data: function () {
    return {
      token: sessionStorage.getItem('token'),
      loginForm: { username: 'Jack', password: '' },
      activities: [],
      prizes: [],
      prizeTotal: 0,
      prizeSearch: '',
      productOptions: [],
      productSearchLoading: false,
      productSearchSequence: 0,
      couponOptions: [],
      couponSearchLoading: false,
      couponSearchSequence: 0,
      pool: [],
      selectedActivity: null,
      activityDialog: false,
      prizeDialog: false,
      poolDialog: false,
      busy: false,
      imageUploading: false,
      prizeImageFileName: '',
      activityForm: { activityName: '', description: '', startTime: '', endTime: '', noWinWeight: 100 },
      prizeForm: { prizeName: '', rewardType: 'POINTS', prizeLevel: 'THIRD', imageUrl: '', description: '', stackable: false, targetId: null, quantity: 1, membershipLevel: 'GOLD', durationDays: 30, membershipProductCode: '' },
      poolForm: { prizeId: null, weight: 1, totalStock: 1, sortOrder: 0 }
    };
  },
  computed: {
    rewardQuantityLabel: function () {
      return ({PRODUCT: '商品件数', POINTS: '奖励积分', COUPON: '优惠券张数', DRAW_CHANCE: '抽奖机会'})[this.prizeForm.rewardType] || '发放数量';
    }
  },
  created: function () {
    if (this.token) {
      this.loadActivities();
      this.loadPrizes();
    }
  },
  methods: {
    unwrap: function (response) {
      return response && response.data && Object.prototype.hasOwnProperty.call(response.data, 'data')
        ? response.data.data : response.data;
    },
    showError: function (error, fallback) {
      var data = error && error.response && error.response.data;
      if (error && error.response && error.response.status === 403) {
        this.$message.error('当前账号没有抽奖管理权限');
        return;
      }
      this.$message.error((data && (data.message || data.msg)) || fallback);
    },
    login: async function () {
      try {
        var response = await axios.post('/users/login', this.loginForm);
        var user = response.data;
        sessionStorage.setItem('token', user.token);
        sessionStorage.setItem('user-info', JSON.stringify(user));
        this.token = user.token;
        await Promise.all([this.loadActivities(), this.loadPrizes()]);
      } catch (error) {
        this.showError(error, '登录失败，请检查账号和密码');
      }
    },
    logout: function () {
      sessionStorage.clear();
      location.reload();
    },
    loadActivities: async function () {
      try {
        var response = await axios.get('/marketing/admin/activities', { params: { page: 1, size: 100 } });
        this.activities = this.unwrap(response).records || [];
      } catch (error) {
        this.showError(error, '活动列表加载失败');
      }
    },
    loadPrizes: async function () {
      try {
        var params = { page: 1, size: 100, status: 1 };
        if (this.prizeSearch.trim()) params.name = this.prizeSearch.trim();
        var response = await axios.get('/marketing/admin/prizes', { params: params });
        var page = this.unwrap(response);
        this.prizes = page.records || [];
        this.prizeTotal = page.total || 0;
      } catch (error) {
        this.showError(error, '奖品列表加载失败');
      }
    },
    searchProducts: async function (keyword) {
      var query = (keyword || '').trim();
      if (!query) {
        this.productOptions = [];
        return;
      }
      var sequence = ++this.productSearchSequence;
      this.productSearchLoading = true;
      try {
        var response = await axios.get('/search/list', {params: {key: query, pageNo: 1, pageSize: 20}});
        if (sequence !== this.productSearchSequence) return;
        var page = this.unwrap(response) || {};
        var results = Array.isArray(page) ? page : (page.list || page.records || []);
        var selected = this.productOptions.find(function (item) {
          return Number(item.id) === Number(this.prizeForm.targetId);
        }, this);
        this.productOptions = selected && !results.some(function (item) { return Number(item.id) === Number(selected.id); })
          ? [selected].concat(results) : results;
      } catch (error) {
        if (sequence === this.productSearchSequence) this.showError(error, '商品搜索失败');
      } finally {
        if (sequence === this.productSearchSequence) this.productSearchLoading = false;
      }
    },
    searchCoupons: async function (keyword) {
      var query = (keyword || '').trim();
      var sequence = ++this.couponSearchSequence;
      this.couponSearchLoading = true;
      try {
        var params = {size: 50};
        if (query) params.keyword = query;
        var response = await axios.get('/marketing/admin/coupon-templates', {
          params: params
        });
        if (sequence !== this.couponSearchSequence) return;
        var results = this.unwrap(response) || [];
        if (!Array.isArray(results)) results = results.records || results.list || [];
        var selected = this.couponOptions.find(function (coupon) {
          return Number(coupon.id) === Number(this.prizeForm.targetId);
        }, this);
        this.couponOptions = selected && !results.some(function (coupon) { return Number(coupon.id) === Number(selected.id); })
          ? [selected].concat(results) : results;
      } catch (error) {
        if (sequence === this.couponSearchSequence) this.showError(error, '优惠券搜索失败');
      } finally {
        if (sequence === this.couponSearchSequence) this.couponSearchLoading = false;
      }
    },
    loadCouponsOnOpen: function (opened) {
      if (!opened || this.prizeForm.rewardType !== 'COUPON') return;
      return this.searchCoupons('');
    },
    formatCents: function (value) {
      var cents = Number(value);
      return '¥' + (Number.isFinite(cents) ? cents / 100 : 0).toFixed(2);
    },
    formatCouponValue: function (coupon) {
      var discount = this.formatCents(coupon && coupon.discountCent);
      if (coupon && coupon.couponType === 'THRESHOLD') {
        return '满 ' + this.formatCents(coupon.thresholdCent) + ' 减 ' + discount;
      }
      return discount + ' 无门槛';
    },
    productOptionLabel: function (item) {
      return (item && item.name || '未命名商品') + ' · ' + this.formatCents(item && item.price);
    },
    couponOptionLabel: function (coupon) {
      return (coupon && coupon.templateName || '未命名优惠券') + ' · ' + this.formatCouponValue(coupon);
    },
    onPrizeTypeChange: function (type) {
      this.prizeForm.targetId = null;
      this.productOptions = [];
      this.couponOptions = [];
      this.productSearchSequence += 1;
      this.couponSearchSequence += 1;
      this.productSearchLoading = false;
      this.couponSearchLoading = false;
      if (type === 'MEMBERSHIP') this.prizeForm.quantity = 1;
    },
    membershipRewardCode: function () {
      if (!this.prizeForm.membershipProductCode) {
        this.prizeForm.membershipProductCode = 'LOTTERY-MEM-' + Date.now().toString(36).toUpperCase()
          + '-' + Math.random().toString(36).slice(2, 8).toUpperCase();
      }
      return this.prizeForm.membershipProductCode;
    },
    uploadPrizeImage: async function (event) {
      var file = event && event.target && event.target.files && event.target.files[0];
      if (!file) return;
      this.imageUploading = true;
      try {
        this.prizeForm.imageUrl = await window.AdminImageUpload.upload(file);
        this.prizeImageFileName = file.name;
        this.$message.success('奖品图片上传成功');
      } catch (error) {
        this.showError(error, '奖品图片上传失败');
      } finally {
        this.imageUploading = false;
        event.target.value = '';
      }
    },
    choosePrizeImage: function () {
      var input = this.$refs && this.$refs.prizeImageFile;
      if (input && typeof input.click === 'function') input.click();
    },
    createActivity: async function () {
      var form = this.activityForm;
      if (!form.activityName.trim() || !form.startTime || !form.endTime || !Number.isInteger(Number(form.noWinWeight))) {
        this.$message.error('请填写活动名称、起止时间和未中奖权重');
        return;
      }
      if (form.endTime <= form.startTime) {
        this.$message.error('结束时间必须晚于开始时间');
        return;
      }
      this.busy = true;
      try {
        await axios.post('/marketing/admin/activities', {
          activityName: form.activityName.trim(), description: form.description.trim(),
          startTime: form.startTime, endTime: form.endTime, noWinWeight: Number(form.noWinWeight)
        });
        this.activityDialog = false;
        this.activityForm = { activityName: '', description: '', startTime: '', endTime: '', noWinWeight: 100 };
        await this.loadActivities();
        this.$message.success('活动已创建，请配置奖池后发布');
      } catch (error) {
        this.showError(error, '创建活动失败');
      } finally {
        this.busy = false;
      }
    },
    createPrize: async function () {
      var form = this.prizeForm;
      var isMembership = form.rewardType === 'MEMBERSHIP';
      var quantity = isMembership ? 1 : Number(form.quantity);
      if (!form.prizeName.trim() || !form.rewardType || !form.prizeLevel || !Number.isInteger(quantity) || quantity <= 0) {
        this.$message.error('请填写奖品名称、发放内容、等级和正整数数量');
        return;
      }
      if (form.rewardType === 'PRODUCT' && (!Number.isInteger(Number(form.targetId)) || Number(form.targetId) <= 0)) {
        this.$message.error('请搜索并选择一个已上架商品');
        return;
      }
      if (form.rewardType === 'COUPON' && (!Number.isInteger(Number(form.targetId)) || Number(form.targetId) <= 0)) {
        this.$message.error('请搜索并选择一个有效优惠券模板');
        return;
      }
      if (isMembership && (!form.membershipLevel.trim() || !Number.isInteger(Number(form.durationDays)) || Number(form.durationDays) <= 0)) {
        this.$message.error('请填写会员等级和正整数天数');
        return;
      }
      this.busy = true;
      try {
        var response = await axios.post('/marketing/admin/prizes', {
          prizeName: form.prizeName.trim(), rewardType: form.rewardType, prizeLevel: form.prizeLevel,
          imageUrl: form.imageUrl.trim() || null, description: form.description.trim() || null,
          stackable: Boolean(form.stackable),
          targetId: form.rewardType === 'PRODUCT' || form.rewardType === 'COUPON' ? Number(form.targetId) : null,
          quantity: quantity,
          membershipProductCode: isMembership ? this.membershipRewardCode() : null,
          membershipLevel: isMembership ? form.membershipLevel.trim() : null,
          durationDays: isMembership ? Number(form.durationDays) : null
        });
        var prize = this.unwrap(response);
        this.poolForm.prizeId = prize.id;
        this.prizeDialog = false;
        this.prizeForm = { prizeName: '', rewardType: 'POINTS', prizeLevel: 'THIRD', imageUrl: '', description: '', stackable: false, targetId: null, quantity: 1, membershipLevel: 'GOLD', durationDays: 30, membershipProductCode: '' };
        this.prizeImageFileName = '';
        await this.loadPrizes();
        this.$message.success('奖品已创建，可加入活动奖池');
      } catch (error) {
        this.showError(error, '创建奖品失败');
      } finally {
        this.busy = false;
      }
    },
    openPool: async function (activity) {
      this.selectedActivity = activity;
      this.poolDialog = true;
      await Promise.all([this.loadPool(), this.loadPrizes()]);
    },
    loadPool: async function () {
      if (!this.selectedActivity) return;
      try {
        var response = await axios.get('/marketing/admin/activities/' + this.selectedActivity.id + '/prizes');
        this.pool = this.unwrap(response) || [];
      } catch (error) {
        this.showError(error, '奖池加载失败');
      }
    },
    addPrizeToActivity: async function () {
      if (!this.selectedActivity || Number(this.poolForm.prizeId) <= 0 || Number(this.poolForm.weight) <= 0
          || Number(this.poolForm.totalStock) <= 0 || Number(this.poolForm.sortOrder) < 0) {
        this.$message.error('请选择奖品，并填写正数权重、库存和展示顺序');
        return;
      }
      this.busy = true;
      try {
        await axios.post('/marketing/admin/activities/' + this.selectedActivity.id + '/prizes', {
          prizeId: Number(this.poolForm.prizeId), weight: Number(this.poolForm.weight),
          totalStock: Number(this.poolForm.totalStock), sortOrder: Number(this.poolForm.sortOrder)
        });
        await this.loadPool();
        this.poolForm = { prizeId: null, weight: 1, totalStock: 1, sortOrder: this.pool.length };
        this.$message.success('奖品已加入活动奖池');
      } catch (error) {
        this.showError(error, '加入奖池失败');
      } finally {
        this.busy = false;
      }
    },
    publish: async function (activity) {
      try {
        await axios.patch('/marketing/admin/activities/' + activity.id + '/publish');
        await this.loadActivities();
        this.$message.success('活动已发布');
      } catch (error) {
        this.showError(error, '发布失败，请先配置可用奖品和库存');
      }
    },
    disable: async function (activity) {
      try {
        await axios.patch('/marketing/admin/activities/' + activity.id + '/disable');
        await this.loadActivities();
        this.$message.success('活动已禁用');
      } catch (error) {
        this.showError(error, '禁用活动失败');
      }
    },
    prizeTypeLabel: function (type) {
      return ({ PHYSICAL: '实物', COUPON: '优惠券', POINTS: '积分', MEMBERSHIP: '会员', DRAW_CHANCE: '抽奖次数' })[type] || type;
    },
    rewardTypeLabel: function (type) {
      return ({ PRODUCT: '商品', COUPON: '优惠券', POINTS: '积分', MEMBERSHIP: '会员', DRAW_CHANCE: '抽奖次数' })[type] || type;
    },
    statusLabel: function (status) {
      return ({ DRAFT: '草稿', SCHEDULED: '待开始', RUNNING: '进行中', ENDED: '已结束', DISABLED: '已禁用' })[status] || status;
    }
  }
});
