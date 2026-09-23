import { useEffect, useState } from 'react'
import {
  Row,
  Col,
  Card,
  Typography,
  Button,
  Tag,
  Modal,
  message,
  Spin,
  Empty,
  List,
  Divider,
  Tooltip,
} from 'antd'
import {
  GiftOutlined,
  GiftFilled,
  CopyOutlined,
  CheckCircleFilled,
} from '@ant-design/icons'
import { pointsApi } from '../api/points'
import type { MallItem, PointsAccount, Coupon } from '../types'
import dayjs from 'dayjs'

const { Title, Text, Paragraph } = Typography

function PointsMall() {
  const [items, setItems] = useState<MallItem[]>([])
  const [account, setAccount] = useState<PointsAccount | null>(null)
  const [coupons, setCoupons] = useState<Coupon[]>([])
  const [loading, setLoading] = useState(false)
  const [redeemModalVisible, setRedeemModalVisible] = useState(false)
  const [selectedItem, setSelectedItem] = useState<MallItem | null>(null)
  // 正在兑换的商品 id，保证连续点击只有一笔请求发出
  const [redeemingId, setRedeemingId] = useState<string | null>(null)
  // 兑换成功后展示的券
  const [redeemedCoupon, setRedeemedCoupon] = useState<Coupon | null>(null)

  useEffect(() => {
    loadData()
  }, [])

  const loadData = async () => {
    setLoading(true)
    try {
      const [itemsRes, accountRes, couponsRes] = await Promise.all([
        pointsApi.getMallItems(),
        pointsApi.getBalance(),
        pointsApi.getCoupons(),
      ])
      setItems(itemsRes.data?.data || [])
      setAccount(accountRes.data?.data || accountRes.data)
      setCoupons(couponsRes.data?.data?.content || [])
    } catch (error) {
      console.error('Failed to load mall data:', error)
    } finally {
      setLoading(false)
    }
  }

  // 兑换成功后只刷新受影响的数据：库存、余额、我的券
  const refreshAfterRedeem = async () => {
    try {
      const [itemsRes, accountRes, couponsRes] = await Promise.all([
        pointsApi.getMallItems(),
        pointsApi.getBalance(),
        pointsApi.getCoupons(),
      ])
      setItems(itemsRes.data?.data || [])
      setAccount(accountRes.data?.data || accountRes.data)
      setCoupons(couponsRes.data?.data?.content || [])
    } catch (error) {
      console.error('Failed to refresh mall data:', error)
    }
  }

  const handleRedeem = async () => {
    if (!selectedItem || redeemingId) return
    setRedeemingId(selectedItem.id)
    try {
      const res = await pointsApi.redeem(selectedItem.id)
      const body = res.data
      // 业务失败（积分不足/已抢完/重复点击）会返回 success=false，HTTP 状态码仍为 200
      if (body && body.success === false) {
        message.error(body.message || '兑换失败')
        return
      }
      const coupon = body?.data as Coupon
      setRedeemedCoupon(coupon)
      message.success('兑换成功，优惠券已发放')
      setRedeemModalVisible(false)
      await refreshAfterRedeem()
    } catch (error: any) {
      // 拦截器已统一弹出后端 message，这里兜底
      const msg = error?.response?.data?.message || '兑换失败，请稍后重试'
      message.error(msg)
    } finally {
      setRedeemingId(null)
    }
  }

  const copyCode = async (code: string) => {
    try {
      await navigator.clipboard.writeText(code)
      message.success('兑换码已复制')
    } catch {
      message.warning('复制失败，请手动记录兑换码')
    }
  }

  const canRedeem = (item: MallItem) => {
    return (account?.balance || 0) >= item.pointsCost && item.stock > 0
  }

  const getRedeemDisabledReason = (item: MallItem): string | null => {
    if (item.stock <= 0) return '已兑换完毕'
    if ((account?.balance || 0) < item.pointsCost) {
      return `积分不足，还差 ${item.pointsCost - (account?.balance || 0)} 积分`
    }
    return null
  }

  const getTypeTag = (type: string) => {
    const colors: Record<string, string> = {
      COUPON: 'orange',
      AUDIO: 'purple',
      EBOOK: 'blue',
    }
    const labels: Record<string, string> = {
      COUPON: '优惠券',
      AUDIO: '音频课程',
      EBOOK: '电子书',
    }
    return <Tag color={colors[type] || 'default'}>{labels[type] || type}</Tag>
  }

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 24 }}>
        <Title level={2} style={{ margin: 0 }}>积分商城</Title>
        {account && (
          <Card size="small">
            <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
              <GiftOutlined style={{ fontSize: 24, color: '#faad14' }} />
              <div>
                <div style={{ fontSize: 12, color: '#999' }}>我的积分</div>
                <div style={{ fontSize: 20, fontWeight: 600, color: '#faad14' }}>
                  {account.balance}
                </div>
              </div>
            </div>
          </Card>
        )}
      </div>

      <Spin spinning={loading}>
        {items.length > 0 ? (
          <Row gutter={[16, 16]}>
            {items.map((item) => {
              const disabledReason = getRedeemDisabledReason(item)
              const soldOut = item.stock <= 0
              return (
                <Col xs={24} sm={12} md={8} lg={6} key={item.id}>
                  <Card
                    hoverable
                    className="card-hover"
                    cover={
                      <div
                        style={{
                          height: 160,
                          background: soldOut
                            ? 'linear-gradient(135deg, #bdc3c7 0%, #95a5a6 100%)'
                            : 'linear-gradient(135deg, #fa709a 0%, #fee140 100%)',
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                          color: '#fff',
                          fontSize: 48,
                          position: 'relative',
                        }}
                      >
                        🎁
                        {soldOut && (
                          <div
                            style={{
                              position: 'absolute',
                              top: 12,
                              right: 12,
                            }}
                          >
                            <Tag color="default" style={{ margin: 0, fontSize: 13, padding: '2px 10px' }}>
                              已抢光
                            </Tag>
                          </div>
                        )}
                      </div>
                    }
                    actions={[
                      disabledReason ? (
                        <Tooltip title={disabledReason}>
                          <Button
                            type="primary"
                            disabled
                            block
                            style={{ margin: '0 12px' }}
                          >
                            {soldOut ? '已兑换完毕' : '积分不足'}
                          </Button>
                        </Tooltip>
                      ) : (
                        <Button
                          type="primary"
                          block
                          loading={redeemingId === item.id}
                          disabled={redeemingId !== null}
                          style={{ margin: '0 12px' }}
                          onClick={() => {
                            setSelectedItem(item)
                            setRedeemModalVisible(true)
                          }}
                        >
                          立即兑换
                        </Button>
                      ),
                    ]}
                  >
                    <Card.Meta
                      title={item.name}
                      description={
                        <div>
                          <div style={{ marginBottom: 8 }}>{getTypeTag(item.type)}</div>
                          {item.description && (
                            <div style={{ color: '#666', fontSize: 12, marginBottom: 8, minHeight: 32 }}>
                              {item.description}
                            </div>
                          )}
                          <div style={{ fontWeight: 600, color: '#faad14' }}>
                            {item.pointsCost} 积分
                          </div>
                          <div style={{ color: soldOut ? '#999' : '#ff4d4f', fontSize: 12, marginTop: 4 }}>
                            剩余份数：{item.stock}
                          </div>
                        </div>
                      }
                    />
                  </Card>
                </Col>
              )
            })}
          </Row>
        ) : (
          <Empty description="暂无商品" style={{ marginTop: 100 }} />
        )}
      </Spin>

      <Divider />

      <Card
        title={
          <span>
            <GiftFilled style={{ marginRight: 8, color: '#fa8c16' }} />
            我的优惠券（{coupons.length}）
          </span>
        }
      >
        {coupons.length > 0 ? (
          <List
            grid={{ gutter: 16, xs: 1, sm: 2, md: 3 }}
            dataSource={coupons}
            renderItem={(coupon) => (
              <List.Item>
                <Card
                  size="small"
                  style={{
                    background: coupon.used
                      ? 'linear-gradient(135deg, #f5f5f5 0%, #e8e8e8 100%)'
                      : 'linear-gradient(135deg, #fff7e6 0%, #ffe7ba 100%)',
                    border: '1px solid #ffd591',
                  }}
                >
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                    <Text strong>{coupon.itemName || '课程优惠券'}</Text>
                    {coupon.used
                      ? <Tag color="default">已使用</Tag>
                      : <Tag color="orange">未使用</Tag>}
                  </div>
                  <div style={{ margin: '8px 0', fontSize: 20, fontWeight: 700, color: '#fa541c' }}>
                    ¥{coupon.discountValue ?? 0}
                    {coupon.minAmount && Number(coupon.minAmount) > 0 && (
                      <span style={{ fontSize: 12, color: '#999', fontWeight: 400 }}>
                        {' '}满 {coupon.minAmount} 可用
                      </span>
                    )}
                  </div>
                  <div style={{ fontSize: 12, color: '#999', marginBottom: 6 }}>
                    有效期至 {dayjs(coupon.validUntil).format('YYYY-MM-DD')}
                  </div>
                  <Tooltip title="点击复制兑换码">
                    <Button
                      size="small"
                      block
                      icon={<CopyOutlined />}
                      disabled={coupon.used}
                      onClick={() => copyCode(coupon.code)}
                    >
                      {coupon.code}
                    </Button>
                  </Tooltip>
                </Card>
              </List.Item>
            )}
          />
        ) : (
          <Empty description="还没有优惠券，去上方兑换吧" image={Empty.PRESENTED_IMAGE_SIMPLE} />
        )}
      </Card>

      <Modal
        title="确认兑换"
        open={redeemModalVisible}
        onOk={handleRedeem}
        onCancel={() => {
          if (redeemingId) return
          setRedeemModalVisible(false)
        }}
        confirmLoading={redeemingId === selectedItem?.id}
        okText="确认兑换"
        cancelText="再想想"
        okButtonProps={{ disabled: redeemingId !== null }}
        cancelButtonProps={{ disabled: redeemingId !== null }}
        maskClosable={!redeemingId}
        closable={!redeemingId}
      >
        {selectedItem && (
          <div>
            <p>
              商品：<strong>{selectedItem.name}</strong>
            </p>
            <p>
              所需积分：
              <span style={{ color: '#faad14', fontWeight: 600 }}>
                {selectedItem.pointsCost} 积分
              </span>
            </p>
            <p>
              当前积分：{account?.balance || 0}
              {(account?.balance || 0) < selectedItem.pointsCost && (
                <Tag color="red" style={{ marginLeft: 8 }}>
                  积分不足
                </Tag>
              )}
            </p>
            <p>
              剩余份数：<span style={{ color: '#ff4d4f' }}>{selectedItem.stock}</span>
            </p>
            <Paragraph type="secondary" style={{ color: '#999', marginBottom: 0 }}>
              确认后将立即扣除积分并占用库存，同时为您生成带唯一兑换码的优惠券，请勿重复点击。
            </Paragraph>
          </div>
        )}
      </Modal>

      <Modal
        title={
          <span>
            <CheckCircleFilled style={{ color: '#52c41a', marginRight: 8 }} />
            兑换成功
          </span>
        }
        open={!!redeemedCoupon}
        onCancel={() => setRedeemedCoupon(null)}
        footer={[
          <Button key="close" type="primary" onClick={() => setRedeemedCoupon(null)}>
            知道了
          </Button>,
        ]}
      >
        {redeemedCoupon && (
          <div style={{ textAlign: 'center', padding: '8px 0' }}>
            <Paragraph style={{ marginBottom: 16 }}>
              优惠券「<strong>{redeemedCoupon.itemName || '课程优惠券'}</strong>」已发放至账户
            </Paragraph>
            <div
              style={{
                border: '2px dashed #fa8c16',
                borderRadius: 8,
                padding: '20px 16px',
                background: '#fff7e6',
              }}
            >
              <div style={{ color: '#999', fontSize: 12, marginBottom: 8 }}>唯一兑换码</div>
              <div style={{ fontSize: 24, fontWeight: 700, letterSpacing: 2, color: '#fa541c' }}>
                {redeemedCoupon.code}
              </div>
              <Button
                type="link"
                icon={<CopyOutlined />}
                onClick={() => copyCode(redeemedCoupon.code)}
              >
                复制兑换码
              </Button>
              <div style={{ color: '#999', fontSize: 12, marginTop: 8 }}>
                有效期至 {dayjs(redeemedCoupon.validUntil).format('YYYY-MM-DD')}
              </div>
            </div>
          </div>
        )}
      </Modal>
    </div>
  )
}

export default PointsMall
