(function (root, factory) {
  var options = factory();
  if (typeof module === 'object' && module.exports) module.exports = options;
  if (root && root.Vue && root.axios && root.document) {
    root.axios.defaults.baseURL = '/api';
    root.axios.defaults.timeout = 10000;
    root.axios.interceptors.request.use(function (config) {
      config.headers.authorization = root.sessionStorage.getItem('token');
      return config;
    });
    new root.Vue(Object.assign({ el: '#app' }, options));
  }
})(typeof window !== 'undefined' ? window : null, function () {
  function responseData(response) {
    var data = response && Object.prototype.hasOwnProperty.call(response, 'data') ? response.data : response;
    return data && Object.prototype.hasOwnProperty.call(data, 'data') ? data.data : data;
  }

  function errorMessage(error, fallback) {
    var body = error && error.response && error.response.data;
    return (body && (body.message || body.msg || body.detail)) || (error && error.message) || fallback;
  }

  var options = {
    data: function () {
      return {
        token: typeof sessionStorage === 'undefined' ? null : sessionStorage.getItem('token'),
        loginForm: { username: '', password: '' },
        loginBusy: false,
        loading: false,
        replayBusy: false,
        loadError: '',
        orders: [],
        total: 0,
        page: 1,
        size: 20,
        filters: { sourceType: '', status: '', sourceId: '', targetUserId: '', waybillNo: '' },
        statusOptions: [
          { value: 'READY', label: '待发货' }, { value: 'FULFILLING', label: '处理中' },
          { value: 'SHIPPED', label: '已发货' }, { value: 'IN_TRANSIT', label: '运输中' },
          { value: 'DELIVERED', label: '已签收' }, { value: 'FAILED', label: '异常' },
          { value: 'TERMINATED', label: '已终止' }
        ],
        trackingOptions: [
          { value: 'PICKED_UP', label: '已揽收' }, { value: 'IN_TRANSIT', label: '运输中' },
          { value: 'OUT_FOR_DELIVERY', label: '派送中' }, { value: 'DELIVERED', label: '已签收' }
        ],
        detailsVisible: false,
        detailsBusy: false,
        selectedOrder: null,
        tracking: [],
        logisticsVisible: false,
        logisticsBusy: false,
        logisticsForm: { carrierCode: '', carrierName: '', waybillNo: '' },
        trackingBusy: false,
        trackingForm: { eventType: 'PICKED_UP', locationSummary: '', description: '', eventTime: '' }
      };
    },
    created: function () {
      if (this.token) this.loadOrders();
    },
    methods: {
      async login() {
        this.loginBusy = true;
        try {
          var user = responseData(await axios.post('/users/login', this.loginForm));
          if (!user || !user.token) throw new Error('登录响应缺少令牌');
          sessionStorage.setItem('token', user.token);
          sessionStorage.setItem('user-info', JSON.stringify(user));
          this.token = user.token;
          this.loginForm.password = '';
          await this.loadOrders();
        } catch (error) {
          this.$message.error(errorMessage(error, '登录失败，请检查账号和密码'));
        } finally {
          this.loginBusy = false;
        }
      },
      logout() {
        sessionStorage.removeItem('token');
        sessionStorage.removeItem('user-info');
        this.token = null;
        this.orders = [];
      },
      async loadOrders() {
        this.loading = true;
        this.loadError = '';
        var params = { page: this.page, size: this.size };
        Object.keys(this.filters).forEach(function (key) {
          var value = this.filters[key];
          if (value !== '' && value !== null && value !== undefined) params[key] = value;
        }, this);
        try {
          var result = responseData(await axios.get('/admin/shipping/orders', { params: params }));
          this.orders = result && Array.isArray(result.records) ? result.records : [];
          this.total = result && Number.isFinite(Number(result.total)) ? Number(result.total) : 0;
        } catch (error) {
          this.loadError = errorMessage(error, '物流列表加载失败');
          this.orders = [];
          this.total = 0;
          if (error && error.response && error.response.status === 403) {
            this.loadError = '当前账号没有物流管理权限，请使用管理员账号登录';
          }
        } finally {
          this.loading = false;
        }
      },
      async replayPaidEvents() {
        this.replayBusy = true;
        try {
          var count = responseData(await axios.post('/admin/shipping/operations/replay-paid-events', null, {
            params: {limit: 100}
          }));
          this.$message.success('已重新投递 ' + Number(count || 0) + ' 条付款物流消息');
        } catch (error) {
          this.$message.error(errorMessage(error, '付款物流消息重试失败'));
        } finally {
          this.replayBusy = false;
        }
      },
      search() {
        this.page = 1;
        return this.loadOrders();
      },
      resetFilters() {
        this.filters = { sourceType: '', status: '', sourceId: '', targetUserId: '', waybillNo: '' };
        return this.search();
      },
      changePage(page) {
        this.page = page;
        return this.loadOrders();
      },
      async openDetails(order) {
        this.selectedOrder = order;
        this.detailsVisible = true;
        this.detailsBusy = true;
        this.trackingForm = {
          eventType: this.nextTrackingEventType(order.status, []),
          locationSummary: '',
          description: '',
          eventTime: ''
        };
        try {
          var base = '/admin/shipping/orders/' + encodeURIComponent(order.shippingNo);
          var values = await Promise.all([axios.get(base), axios.get(base + '/tracking')]);
          this.selectedOrder = responseData(values[0]);
          var tracking = responseData(values[1]);
          this.tracking = Array.isArray(tracking) ? tracking.slice().reverse() : [];
          this.trackingForm.eventType = this.nextTrackingEventType(this.selectedOrder.status, this.tracking);
        } catch (error) {
          this.$message.error(errorMessage(error, '物流详情加载失败'));
        } finally {
          this.detailsBusy = false;
        }
      },
      clearSelected() {
        this.selectedOrder = null;
        this.tracking = [];
      },
      openLogistics(order) {
        this.selectedOrder = order;
        this.logisticsForm = {
          carrierCode: order.carrierCode || '',
          carrierName: order.carrierName || '',
          waybillNo: order.waybillNo || ''
        };
        this.logisticsVisible = true;
      },
      async saveLogistics() {
        if (!this.selectedOrder || !this.logisticsForm.carrierCode.trim()
          || !this.logisticsForm.carrierName.trim() || !this.logisticsForm.waybillNo.trim()) {
          this.$message.warning('请填写快递编码、快递公司和运单号');
          return;
        }
        this.logisticsBusy = true;
        try {
          var base = '/admin/shipping/orders/' + encodeURIComponent(this.selectedOrder.shippingNo);
          var result = responseData(await axios.put(base + '/logistics', {
            carrierCode: this.logisticsForm.carrierCode.trim(),
            carrierName: this.logisticsForm.carrierName.trim(),
            waybillNo: this.logisticsForm.waybillNo.trim()
          }));
          if (result) this.selectedOrder = result;
          this.logisticsVisible = false;
          this.$message.success('运单信息已保存');
          await this.loadOrders();
          if (this.detailsVisible) await this.refreshDetails();
        } catch (error) {
          this.$message.error(errorMessage(error, '运单信息保存失败'));
        } finally {
          this.logisticsBusy = false;
        }
      },
      async addTrackingEvent() {
        if (!this.selectedOrder || !this.trackingForm.description.trim()) {
          this.$message.warning('请填写物流说明');
          return;
        }
        if (!this.selectedOrder.waybillNo) {
          this.$message.warning('请先录入快递公司和运单号');
          return;
        }
        this.trackingBusy = true;
        try {
          var base = '/admin/shipping/orders/' + encodeURIComponent(this.selectedOrder.shippingNo);
          await axios.post(base + '/tracking', {
            eventType: this.trackingForm.eventType,
            locationSummary: this.trackingForm.locationSummary.trim() || null,
            description: this.trackingForm.description.trim(),
            eventTime: this.trackingForm.eventTime || null
          });
          this.trackingForm = { eventType: 'IN_TRANSIT', locationSummary: '', description: '', eventTime: '' };
          this.$message.success('物流节点已添加');
          await Promise.all([this.refreshDetails(), this.loadOrders()]);
        } catch (error) {
          this.$message.error(errorMessage(error, '物流节点添加失败'));
        } finally {
          this.trackingBusy = false;
        }
      },
      async refreshDetails() {
        if (!this.selectedOrder) return;
        var base = '/admin/shipping/orders/' + encodeURIComponent(this.selectedOrder.shippingNo);
        var values = await Promise.all([axios.get(base), axios.get(base + '/tracking')]);
        this.selectedOrder = responseData(values[0]);
        var tracking = responseData(values[1]);
        this.tracking = Array.isArray(tracking) ? tracking.slice().reverse() : [];
        this.trackingForm.eventType = this.nextTrackingEventType(this.selectedOrder.status, this.tracking);
      },
      nextTrackingEventType(status, tracking) {
        if (status === 'READY' || status === 'FULFILLING') return 'PICKED_UP';
        if (status === 'SHIPPED') return 'IN_TRANSIT';
        if (status === 'IN_TRANSIT') {
          return tracking && tracking[0] && tracking[0].eventType === 'OUT_FOR_DELIVERY'
            ? 'DELIVERED'
            : 'OUT_FOR_DELIVERY';
        }
        return 'PICKED_UP';
      },
      sourceLabel(value) {
        return ({ CASH_ORDER: '商城订单', LOTTERY_BENEFIT: '抽奖实物', POINTS_REDEMPTION: '积分兑换' })[value] || value || '—';
      },
      statusLabel(value) {
        var item = this.statusOptions.find(function (option) { return option.value === value; });
        return item ? item.label : (value || '未知');
      },
      statusTone(value) {
        return ({ READY: 'info', FULFILLING: 'warning', SHIPPED: '', IN_TRANSIT: 'primary', DELIVERED: 'success', FAILED: 'danger', TERMINATED: 'info' })[value] || 'info';
      },
      isTerminal(value) {
        return value === 'DELIVERED' || value === 'TERMINATED';
      },
      eventTypeLabel(value) {
        var item = this.trackingOptions.find(function (option) { return option.value === value; });
        return item ? item.label : (value || '物流更新');
      }
    }
  };

  return options;
});
