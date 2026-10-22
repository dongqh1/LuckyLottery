(function (root, factory) {
  var options = factory();
  if (typeof module === 'object' && module.exports) module.exports = options;
  if (root) root.userDrawChanceMixin = options;
})(typeof window !== 'undefined' ? window : null, function () {
  var DRAW_CHANCE_REQUEST_TIMEOUT = 15000;
  function unwrap(response) {
    var data = response && Object.prototype.hasOwnProperty.call(response, 'data') ? response.data : response;
    return data && Object.prototype.hasOwnProperty.call(data, 'data') ? data.data : data;
  }

  function notify(vm, type, message) {
    if (typeof vm.$message === 'function') {
      vm.$message({type: type, message: message});
    } else if (vm.$message && typeof vm.$message[type] === 'function') {
      vm.$message[type](message);
    }
  }

  function errorMessage(error, fallback) {
    var response = error && error.response;
    var body = response && response.data;
    if (response && response.status === 403) return '当前账号没有抽奖管理权限';
    return (body && (body.message || body.msg || body.detail)) || (error && error.message) || fallback;
  }

  function adjustmentFingerprint(userId, form) {
    return [userId, form.direction, Number(form.amount), form.reason.trim()].join('|');
  }

  function newAdjustmentId() {
    return 'admin-' + Date.now().toString(36) + '-' + Math.random().toString(36).slice(2, 12);
  }

  return {
    data: function () {
      return {
        selectedChanceUser: null,
        drawChanceDialogVisible: false,
        drawChanceBusy: false,
        drawChanceLoadKey: 0,
        drawChanceAccount: null,
        drawChanceLedger: [],
        drawChanceLedgerTotal: 0,
        drawChanceLedgerPage: 1,
        chanceAdjustmentForm: {direction: 'CREDIT', amount: 1, reason: ''},
        pendingAdjustmentId: null,
        pendingAdjustmentFingerprint: null
      };
    },
    methods: {
      openDrawChanceDialog: async function (user) {
        var userId = Number(user && user.id);
        if (!Number.isInteger(userId) || userId <= 0) {
          notify(this, 'error', '用户 ID 无效');
          return;
        }
        var loadKey = ++this.drawChanceLoadKey;
        this.selectedChanceUser = {id: userId, username: user.username || ''};
        this.drawChanceDialogVisible = true;
        this.drawChanceAccount = null;
        this.drawChanceLedger = [];
        this.drawChanceLedgerTotal = 0;
        this.drawChanceLedgerPage = 1;
        this.chanceAdjustmentForm = {direction: 'CREDIT', amount: 1, reason: ''};
        this.pendingAdjustmentId = null;
        this.pendingAdjustmentFingerprint = null;
        this.drawChanceBusy = true;
        try {
          if (await this.loadDrawChanceAccount(userId, loadKey)) {
            await this.loadDrawChanceLedger(1, userId, loadKey);
          }
        } finally {
          if (loadKey === this.drawChanceLoadKey) this.drawChanceBusy = false;
        }
      },
      loadDrawChanceAccount: async function (userId, loadKey) {
        userId = Number(userId || (this.selectedChanceUser && this.selectedChanceUser.id));
        loadKey = loadKey === undefined ? this.drawChanceLoadKey : loadKey;
        if (!Number.isInteger(userId) || userId <= 0) return false;
        try {
          var response = await axios.get('/marketing/admin/draw-chances/users/' + userId, {
            timeout: DRAW_CHANCE_REQUEST_TIMEOUT
          });
          if (loadKey === this.drawChanceLoadKey && this.selectedChanceUser && this.selectedChanceUser.id === userId) {
            this.drawChanceAccount = unwrap(response);
          }
          return true;
        } catch (error) {
          if (loadKey === this.drawChanceLoadKey) notify(this, 'error', errorMessage(error, '抽奖机会账户加载失败'));
          return false;
        }
      },
      loadDrawChanceLedger: async function (page, userId, loadKey) {
        userId = Number(userId || (this.selectedChanceUser && this.selectedChanceUser.id));
        loadKey = loadKey === undefined ? this.drawChanceLoadKey : loadKey;
        if (!Number.isInteger(userId) || userId <= 0) return;
        var requestedPage = Number(page) || 1;
        this.drawChanceLedgerPage = requestedPage;
        try {
          var response = await axios.get('/marketing/admin/draw-chances/users/' + userId + '/ledger', {
            params: {page: requestedPage, size: 20},
            timeout: DRAW_CHANCE_REQUEST_TIMEOUT
          });
          if (loadKey === this.drawChanceLoadKey && this.drawChanceLedgerPage === requestedPage
              && this.selectedChanceUser && this.selectedChanceUser.id === userId) {
            var result = unwrap(response);
            this.drawChanceLedger = result.records || [];
            this.drawChanceLedgerTotal = result.total || 0;
          }
        } catch (error) {
          if (loadKey === this.drawChanceLoadKey && this.drawChanceLedgerPage === requestedPage) {
            notify(this, 'error', errorMessage(error, '抽奖机会流水加载失败'));
          }
        }
      },
      adjustDrawChances: async function () {
        var userId = Number(this.selectedChanceUser && this.selectedChanceUser.id);
        var username = this.selectedChanceUser && this.selectedChanceUser.username;
        var form = this.chanceAdjustmentForm;
        var amount = Number(form.amount);
        var reason = String(form.reason || '').trim();
        if (!Number.isInteger(userId) || userId <= 0 || !Number.isInteger(amount) || amount <= 0 || !reason) {
          notify(this, 'error', '请填写正整数数量和调整原因');
          return;
        }
        var fingerprint = adjustmentFingerprint(userId, form);
        if (!this.pendingAdjustmentId || this.pendingAdjustmentFingerprint !== fingerprint) {
          this.pendingAdjustmentId = newAdjustmentId();
          this.pendingAdjustmentFingerprint = fingerprint;
        }
        var adjustmentId = this.pendingAdjustmentId;
        var loadKey = this.drawChanceLoadKey;
        this.drawChanceBusy = true;
        try {
          await axios.post('/marketing/admin/draw-chances/users/' + userId + '/adjustments', {
            adjustmentId: adjustmentId,
            direction: form.direction,
            amount: amount,
            reason: reason
          }, {timeout: DRAW_CHANCE_REQUEST_TIMEOUT});
          if (loadKey === this.drawChanceLoadKey && this.selectedChanceUser && this.selectedChanceUser.id === userId) {
            this.pendingAdjustmentId = null;
            this.pendingAdjustmentFingerprint = null;
            this.chanceAdjustmentForm = {direction: 'CREDIT', amount: 1, reason: ''};
            await this.loadDrawChanceAccount(userId, loadKey);
            await this.loadDrawChanceLedger(1, userId, loadKey);
          }
          var action = form.direction === 'CREDIT' ? '抽奖机会已增加' : '抽奖机会已扣除';
          notify(this, 'success', username ? username + '：' + action : action);
        } catch (error) {
          notify(this, 'error', errorMessage(error, '调整抽奖机会失败'));
        } finally {
          if (loadKey === this.drawChanceLoadKey) this.drawChanceBusy = false;
        }
      },
      businessTypeLabel: function (businessType) {
        var labels = {
          LOTTERY_REWARD: '抽奖奖励',
          DRAW_CONSUME: '抽奖消耗',
          DRAW_RELEASE: '抽奖退回',
          MANUAL_ADJUSTMENT: '管理员调整'
        };
        return labels[businessType] || businessType || '-';
      },
      directionLabel: function (direction) {
        return direction === 'CREDIT' ? '增加' : direction === 'DEBIT' ? '扣除' : direction || '-';
      }
    }
  };
});
