/* eslint-disable */
import request from "@/request";

/**
 * 创建 Stripe Checkout 收银台会话
 * POST /payment/create
 * @return BaseResponse<String>，data 为收银台跳转 URL
 */
export async function createPayment(options?: { [key: string]: any }) {
  return request<API.BaseResponseString>("/payment/create", {
    method: "POST",
    ...(options || {}),
  });
}

/**
 * 申请退款（仅 VIP，后端校验角色）
 * POST /payment/refund?reason=
 */
export async function refundPayment(
  params: { reason?: string },
  options?: { [key: string]: any },
) {
  return request<API.BaseResponseBoolean>("/payment/refund", {
    method: "POST",
    params: {
      ...params,
    },
    ...(options || {}),
  });
}
