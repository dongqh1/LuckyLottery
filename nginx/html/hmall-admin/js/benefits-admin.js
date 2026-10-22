axios.defaults.baseURL = '/api';
axios.defaults.timeout = 10000;
axios.interceptors.request.use(function (config) {
  config.headers.authorization = sessionStorage.getItem('token');
  return config;
});

function requestData(response) {
  var data = response && Object.prototype.hasOwnProperty.call(response, 'data') ? response.data : response;
  return data && Object.prototype.hasOwnProperty.call(data, 'data') ? data.data : data;
}

function newBusinessRef(prefix) {
  return prefix + '-' + Date.now() + '-' + Math.random().toString(36).slice(2, 8).toUpperCase();
}

new Vue({
  el: '#app',
  data: function () {
    return {
      token: sessionStorage.getItem('token'),
      loginForm: { username: 'Jack', password: '' },
      activeTab: 'coupons',
      couponForm: {
        templateCode: '', templateName: '', couponType: 'THRESHOLD', thresholdCent: 10000,
        discountCent: 1000, scopeType: 'ALL', hmallItemId: null, hmallCategory: '', hmallBrand: '',
        validFrom: '', validUntil: '', perUserLimit: 1, stackableWithMembership: true
      },
      couponItemOptions: [], couponItemSearchLoading: false, couponItemSearchSequence: 0, couponItemSearchError: '',
      issueForm: { templateId: null, userId: null, businessNo: '', couponNo: '' },
      createdTemplates: [], lastIssuedCoupon: null,
      membershipForm: {
        productCode: '', productName: '', membershipLevel: 'GOLD', cardType: 'MONTH', durationDays: 30,
        priceCent: 0, discountBasisPoints: 9000, dailyDrawBonus: 0, pointsMultiplierBasisPoints: 10000
      },
      membershipProducts: [],
      pointsOfferForm: { hmallItemId: null, unitPoints: 100, availableStock: 100 },
      pointsItemOptions: [], pointsItemSearchLoading: false, pointsItemSearchSequence: 0, pointsItemSearchError: '',
      pointsOffers: [],
      adjustmentForm: { userId: null, delta: 0, businessId: '', reason: '' },
      reverseForm: { redemptionNo: '', reversalNo: '', reason: '' },
      lastAdjustment: null, lastReversal: null,
      couponBusy: false, issueBusy: false, membershipBusy: false, pointsOfferBusy: false,
      adjustmentBusy: false, reverseBusy: false,
      couponError: '', issueError: '', membershipError: '', pointsError: '', adjustmentError: '', reverseError: ''
    };
  },
  created: function () {
    this.generateIssueNumbers();
    if (this.token) this.loadLists();
  },
  methods: {
    errorText: function (error, fallback) {
      var body = error && error.response && error.response.data;
      return (body && (body.message || body.msg || body.detail)) || (error && error.message) || fallback;
    },
    formatCents: function (value) {
      var cents = Number(value);
      return '¥' + (Number.isFinite(cents) ? cents / 100 : 0).toFixed(2);
    },
    productOptionLabel: function (item) {
      return (item && item.name || '未命名商品') + ' · ' + this.formatCents(item && item.price);
    },
    searchCouponItems: function (keyword) {
      return this.searchMallItems(keyword, 'coupon');
    },
    searchPointsItems: function (keyword) {
      return this.searchMallItems(keyword, 'points');
    },
    searchMallItems: async function (keyword, target) {
      var query = (keyword || '').trim();
      var optionsKey = target === 'coupon' ? 'couponItemOptions' : 'pointsItemOptions';
      var loadingKey = target === 'coupon' ? 'couponItemSearchLoading' : 'pointsItemSearchLoading';
      var sequenceKey = target === 'coupon' ? 'couponItemSearchSequence' : 'pointsItemSearchSequence';
      var errorKey = target === 'coupon' ? 'couponItemSearchError' : 'pointsItemSearchError';
      var form = target === 'coupon' ? this.couponForm : this.pointsOfferForm;
      if (!query) {
        this[sequenceKey] += 1;
        this[optionsKey] = [];
        this[loadingKey] = false;
        this[errorKey] = '';
        return;
      }
      var sequence = ++this[sequenceKey];
      this[loadingKey] = true;
      this[errorKey] = '';
      try {
        var response = await axios.get('/search/list', {params: {key: query, pageNo: 1, pageSize: 20}});
        if (sequence !== this[sequenceKey]) return;
        var page = requestData(response) || {};
        var results = Array.isArray(page) ? page : (page.list || page.records || []);
        var selected = this[optionsKey].find(function (item) {
          return Number(item.id) === Number(form.hmallItemId);
        });
        this[optionsKey] = selected && !results.some(function (item) { return Number(item.id) === Number(selected.id); })
          ? [selected].concat(results) : results;
      } catch (error) {
        if (sequence === this[sequenceKey]) this[errorKey] = this.errorText(error, '商城商品搜索失败');
      } finally {
        if (sequence === this[sequenceKey]) this[loadingKey] = false;
      }
    },
    login: async function () {
      try {
        var response = await axios.post('/users/login', this.loginForm);
        var user = requestData(response);
        sessionStorage.setItem('token', user.token);
        sessionStorage.setItem('user-info', JSON.stringify(user));
        this.token = user.token;
        await this.loadLists();
      } catch (error) {
        this.$message.error(this.errorText(error, '登录失败，请检查账号和密码'));
      }
    },
    logout: function () {
      sessionStorage.removeItem('token');
      sessionStorage.removeItem('user-info');
      location.reload();
    },
    loadLists: async function () {
      await Promise.all([this.loadMembershipProducts(), this.loadPointsOffers()]);
    },
    loadMembershipProducts: async function () {
      this.membershipError = '';
      try {
        var response = await axios.get('/marketing/memberships/products');
        this.membershipProducts = requestData(response) || [];
      } catch (error) {
        this.membershipError = this.errorText(error, '会员商品加载失败');
      }
    },
    loadPointsOffers: async function () {
      this.pointsError = '';
      try {
        var response = await axios.get('/marketing/points/offers');
        this.pointsOffers = requestData(response) || [];
      } catch (error) {
        this.pointsError = this.errorText(error, '积分兑换商品加载失败');
      }
    },
    createCouponTemplate: async function () {
      var form = this.couponForm;
      this.couponError = '';
      if (!form.templateCode.trim() || !form.templateName.trim() || !form.validFrom || !form.validUntil) {
        this.couponError = '请填写模板编码、名称和有效期';
        return;
      }
      if (form.validUntil <= form.validFrom) {
        this.couponError = '结束时间必须晚于开始时间';
        return;
      }
      if (form.couponType === 'THRESHOLD' && Number(form.thresholdCent) <= 0) {
        this.couponError = '满减券的最低消费必须大于 0';
        return;
      }
      if (Number(form.discountCent) <= 0) {
        this.couponError = '优惠金额必须大于 0';
        return;
      }
      if (form.scopeType === 'ITEM'
          && (!Number.isInteger(Number(form.hmallItemId)) || Number(form.hmallItemId) <= 0)
          || form.scopeType === 'CATEGORY' && !form.hmallCategory.trim()
          || form.scopeType === 'BRAND' && !form.hmallBrand.trim()) {
        this.couponError = '请为指定商品范围搜索并选择商品，或填写所选分类/品牌';
        return;
      }
      this.couponBusy = true;
      try {
        var response = await axios.post('/marketing/admin/coupon-templates', {
          templateCode: form.templateCode.trim(), templateName: form.templateName.trim(),
          couponType: form.couponType, thresholdCent: form.couponType === 'NO_THRESHOLD' ? 0 : Number(form.thresholdCent),
          discountCent: Number(form.discountCent), applicableProductId: null,
          scopeType: form.scopeType, hmallItemId: form.scopeType === 'ITEM' ? Number(form.hmallItemId) : null,
          hmallCategory: form.scopeType === 'CATEGORY' ? form.hmallCategory.trim() : null,
          hmallBrand: form.scopeType === 'BRAND' ? form.hmallBrand.trim() : null,
          validFrom: form.validFrom, validUntil: form.validUntil,
          perUserLimit: Number(form.perUserLimit), stackableWithMembership: Boolean(form.stackableWithMembership)
        });
        var template = requestData(response);
        this.createdTemplates.unshift(template);
        this.issueForm.templateId = template.id;
        this.$message.success('优惠券模板已创建');
      } catch (error) {
        this.couponError = this.errorText(error, '创建优惠券模板失败');
      } finally {
        this.couponBusy = false;
      }
    },
    generateIssueNumbers: function () {
      this.issueForm.businessNo = newBusinessRef('COUPON-ISSUE');
      this.issueForm.couponNo = newBusinessRef('COUPON');
    },
    issueCoupon: async function () {
      var form = this.issueForm;
      this.issueError = '';
      if (Number(form.templateId) <= 0 || Number(form.userId) <= 0 || !form.businessNo.trim() || !form.couponNo.trim()) {
        this.issueError = '请填写模板 ID、用户 ID 和两个业务编号';
        return;
      }
      this.issueBusy = true;
      try {
        var response = await axios.post('/marketing/admin/coupons/issues', {
          templateId: Number(form.templateId), userId: Number(form.userId),
          businessNo: form.businessNo.trim(), couponNo: form.couponNo.trim()
        });
        this.lastIssuedCoupon = requestData(response);
        this.generateIssueNumbers();
        this.$message.success('优惠券已发放');
      } catch (error) {
        this.issueError = this.errorText(error, '发放优惠券失败');
      } finally {
        this.issueBusy = false;
      }
    },
    createMembershipProduct: async function () {
      var form = this.membershipForm;
      this.membershipError = '';
      if (!form.productCode.trim() || !form.productName.trim() || !form.membershipLevel.trim()) {
        this.membershipError = '请填写商品编码、名称和会员等级';
        return;
      }
      this.membershipBusy = true;
      try {
        await axios.post('/marketing/admin/membership-products', {
          productCode: form.productCode.trim(), productName: form.productName.trim(),
          membershipLevel: form.membershipLevel.trim(), cardType: form.cardType,
          durationDays: Number(form.durationDays), priceCent: Number(form.priceCent),
          discountBasisPoints: Number(form.discountBasisPoints), dailyDrawBonus: Number(form.dailyDrawBonus),
          pointsMultiplierBasisPoints: Number(form.pointsMultiplierBasisPoints)
        });
        await this.loadMembershipProducts();
        this.$message.success('会员商品已创建');
      } catch (error) {
        this.membershipError = this.errorText(error, '创建会员商品失败');
      } finally {
        this.membershipBusy = false;
      }
    },
    createPointsOffer: async function () {
      var form = this.pointsOfferForm;
      this.pointsError = '';
      if (!Number.isInteger(Number(form.hmallItemId)) || Number(form.hmallItemId) <= 0
          || Number(form.unitPoints) <= 0 || Number(form.availableStock) <= 0) {
        this.pointsError = '请搜索并选择商城商品，并填写有效的兑换积分和库存';
        return;
      }
      this.pointsOfferBusy = true;
      try {
        await axios.post('/marketing/admin/points/offers', {
          hmallItemId: Number(form.hmallItemId), unitPoints: Number(form.unitPoints),
          availableStock: Number(form.availableStock)
        });
        await this.loadPointsOffers();
        this.$message.success('积分兑换商品已创建');
      } catch (error) {
        this.pointsError = this.errorText(error, '创建积分兑换商品失败');
      } finally {
        this.pointsOfferBusy = false;
      }
    },
    adjustPoints: async function () {
      var form = this.adjustmentForm;
      this.adjustmentError = '';
      if (Number(form.userId) <= 0 || !Number.isInteger(Number(form.delta)) || Number(form.delta) === 0
          || !form.businessId.trim() || !form.reason.trim()) {
        this.adjustmentError = '请填写用户 ID、非零整数积分、业务编号和调整原因';
        return;
      }
      this.adjustmentBusy = true;
      try {
        var response = await axios.post('/marketing/admin/points/adjustments', {
          userId: Number(form.userId), delta: Number(form.delta),
          businessId: form.businessId.trim(), reason: form.reason.trim()
        });
        this.lastAdjustment = requestData(response);
        this.$message.success('积分调整已提交');
      } catch (error) {
        this.adjustmentError = this.errorText(error, '积分调整失败');
      } finally {
        this.adjustmentBusy = false;
      }
    },
    reverseRedemption: async function () {
      var form = this.reverseForm;
      this.reverseError = '';
      if (!form.redemptionNo.trim() || !form.reversalNo.trim() || !form.reason.trim()) {
        this.reverseError = '请填写兑换编号、冲正业务编号和原因';
        return;
      }
      this.reverseBusy = true;
      try {
        var response = await axios.post('/marketing/admin/points/redemptions/'
          + encodeURIComponent(form.redemptionNo.trim()) + '/reverse', {
          reversalNo: form.reversalNo.trim(), reason: form.reason.trim()
        });
        this.lastReversal = requestData(response);
        this.$message.success('积分兑换已冲正');
      } catch (error) {
        this.reverseError = this.errorText(error, '冲正积分兑换失败');
      } finally {
        this.reverseBusy = false;
      }
    },
    couponTypeText: function (type) { return ({ NO_THRESHOLD: '无门槛券', THRESHOLD: '满减券' })[type] || type || '—'; },
    cardTypeText: function (type) { return ({ MONTH: '月卡', QUARTER: '季卡', YEAR: '年卡' })[type] || type || '—'; },
    money: function (cent) { return ((Number(cent) || 0) / 100).toFixed(2); }
  }
});
