<template>
  <div class="vip-page">
    <div class="vip-card">
      <div class="vip-header">
        <CrownOutlined class="vip-icon" />
        <h2>会员中心</h2>
      </div>

      <a-alert
        v-if="route.query.success === 'true'"
        type="success"
        show-icon
        message="支付成功，会员已开通（若未到账请稍后刷新）"
        class="callback-alert"
      />
      <a-alert
        v-else-if="route.query.cancelled === 'true'"
        type="info"
        show-icon
        message="已取消支付"
        class="callback-alert"
      />

      <!-- 管理员：显示管理员标识，无需开通会员 -->
      <div v-if="isAdmin" class="plan-card admin-card">
        <div class="plan-title">
          <CrownOutlined />
          你是管理员
        </div>
        <div class="admin-tip">管理员已享有全部功能权限，无需开通会员</div>
      </div>

      <div v-else class="plan-card">
        <div class="plan-title">
          <CrownOutlined />
          永久会员
        </div>
        <div class="plan-price">¥199<span class="plan-unit"> / 永久</span></div>
        <ul class="plan-features">
          <li>创作配额大幅提升</li>
          <li>解锁 VIP 专属配图方式</li>
          <li>后续新功能优先体验</li>
        </ul>
        <a-button type="primary" size="large" :loading="buying" :disabled="isVip" @click="handleBuy">
          {{ isVip ? '您已是会员' : '立即开通' }}
        </a-button>
      </div>

      <div v-if="isVip" class="refund-section">
        <a-divider>退款</a-divider>
        <div class="refund-row">
          <a-input v-model:value="refundReason" placeholder="退款原因（可选）" class="refund-input" />
          <a-popconfirm title="确定申请退款？退款后会员权益将立即取消" @confirm="handleRefund">
            <a-button danger :loading="refunding">申请退款</a-button>
          </a-popconfirm>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { message } from 'ant-design-vue'
import { CrownOutlined } from '@ant-design/icons-vue'
import { useLoginUserStore } from '@/stores/loginUser.ts'
import { createPayment, refundPayment } from '@/api/paymentController.ts'

const route = useRoute()
const loginUserStore = useLoginUserStore()

const buying = ref(false)
const refunding = ref(false)
const refundReason = ref('')

const isVip = computed(() => loginUserStore.loginUser.userRole === 'vip')
const isAdmin = computed(() => loginUserStore.loginUser.userRole === 'admin')

// Stripe 回调后 webhook 入账可能有延迟：轮询刷新登录态（3 次 × 2s）
const refreshLoginUser = async () => {
  for (let i = 0; i < 3; i++) {
    await loginUserStore.fetchLoginUser()
    if (isVip.value) {
      break
    }
    await new Promise((resolve) => setTimeout(resolve, 2000))
  }
}

onMounted(async () => {
  await loginUserStore.fetchLoginUser()
  if (route.query.success === 'true') {
    message.success('支付成功，会员已开通（若未到账请稍后刷新）')
    await refreshLoginUser()
  } else if (route.query.cancelled === 'true') {
    message.info('已取消支付')
  }
})

// 购买：跳转 Stripe 收银台
const handleBuy = async () => {
  buying.value = true
  try {
    const res = await createPayment()
    if (res.data.code === 0 && res.data.data) {
      window.location.href = res.data.data
    } else {
      message.error('创建支付失败：' + res.data.message)
    }
  } catch (e) {
    message.error('创建支付失败，请稍后重试')
  } finally {
    buying.value = false
  }
}

// 退款：成功后刷新登录态，徽章消失
const handleRefund = async () => {
  refunding.value = true
  try {
    const res = await refundPayment({ reason: refundReason.value })
    if (res.data.code === 0) {
      message.success('退款申请已提交，会员权益已取消')
      refundReason.value = ''
      await loginUserStore.fetchLoginUser()
    } else {
      message.error('退款失败：' + res.data.message)
    }
  } catch (e) {
    message.error('退款失败，请稍后重试')
  } finally {
    refunding.value = false
  }
}
</script>

<style scoped>
.vip-page {
  max-width: 720px;
  margin: 48px auto;
  padding: 0 24px;
}

.vip-card {
  background: var(--glass-bg);
  backdrop-filter: var(--glass-blur);
  border: 1px solid var(--color-border);
  border-radius: 16px;
  padding: 32px;
}

.vip-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 24px;
}

.vip-header h2 {
  margin: 0;
}

.vip-icon {
  font-size: 28px;
  color: #faad14;
}

.callback-alert {
  margin-bottom: 24px;
}

.plan-card {
  text-align: center;
  border: 1px solid var(--color-border);
  border-radius: 12px;
  padding: 32px 24px;
}

.plan-title {
  font-size: 20px;
  font-weight: 600;
  color: #faad14;
}

.plan-price {
  font-size: 40px;
  font-weight: 700;
  margin: 16px 0;
}

.plan-unit {
  font-size: 14px;
  font-weight: 400;
  color: var(--color-text-secondary, #888);
}

.plan-features {
  list-style: none;
  padding: 0;
  margin: 0 0 24px;
  color: var(--color-text-secondary, #666);
}

.plan-features li {
  padding: 4px 0;
}

.admin-tip {
  margin-top: 16px;
  color: var(--color-text-secondary, #666);
}

.refund-section {
  margin-top: 24px;
}

.refund-row {
  display: flex;
  gap: 12px;
}

.refund-input {
  flex: 1;
}
</style>
