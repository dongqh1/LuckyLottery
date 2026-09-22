Vue.component('marketing-header', {
  props: ['active'],
  data() {
    return { keyword: '' }
  },
  template: `
    <div class="header marketing-store-header">
      <div class="py-container">
        <div class="yui3-g Logo">
          <div class="yui3-u Left logoArea">
            <a href="/"><div class="logo"><img src="img/logo.png" alt="黑马商城"></div></a>
          </div>
          <div class="yui3-u Center searchArea">
            <div class="search">
              <div class="sui-form form-inline">
                <div class="input-append">
                  <input v-model="keyword" @keyup.enter="search" class="input-error input-xxlarge" placeholder="搜索商城商品">
                  <button @click="search" class="sui-btn btn-xlarge btn-danger" type="button">搜索</button>
                </div>
              </div>
            </div>
            <div class="hotwords"><ul>
              <li class="f-item"><a href="/search.html">商城好物</a></li>
              <li class="f-item"><a href="/lottery.html">幸运抽奖</a></li>
              <li class="f-item"><a href="/points.html">积分兑换</a></li>
              <li class="f-item"><a href="/my-orders.html">我的订单</a></li>
              <li class="f-item"><a href="/coupons.html">我的优惠券</a></li>
              <li class="f-item"><a href="/benefits.html">会员权益</a></li>
            </ul></div>
          </div>
          <div class="yui3-u Right shopArea">
            <div class="fr shopcar"><div class="show-shopcar">
              <span class="car"></span>
              <a class="sui-btn btn-default btn-xlarge" href="/cart.html"><span>我的购物车</span></a>
            </div></div>
          </div>
        </div>
        <div class="yui3-g marketing-nav-row">
          <div class="yui3-u Left all-sort"><h4>商城营销中心</h4></div>
          <div class="yui3-u Center navArea"><ul class="nav">
            <li class="f-item"><a href="/">商城首页</a></li>
            <li class="f-item" :class="{active:active==='lottery'}"><a href="/lottery.html">幸运抽奖</a></li>
            <li class="f-item" :class="{active:active==='points'}"><a href="/points.html">我的积分</a></li>
            <li class="f-item" :class="{active:active==='orders'}"><a href="/my-orders.html">我的订单</a></li>
            <li class="f-item" :class="{active:active==='coupons'}"><a href="/coupons.html">我的优惠券</a></li>
            <li class="f-item" :class="{active:active==='benefits'}"><a href="/benefits.html">我的权益</a></li>
          </ul></div>
          <div class="yui3-u Right"></div>
        </div>
      </div>
    </div>`,
  methods: {
    search() {
      const keyword = this.keyword.trim()
      location.href = '/search.html' + (keyword ? '?key=' + encodeURIComponent(keyword) : '')
    }
  }
})
