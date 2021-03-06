package org.tio.core;

import java.nio.ByteBuffer;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tio.core.intf.Packet;
import org.tio.core.intf.PacketWithMeta;
import org.tio.core.task.SendRunnable;
import org.tio.core.utils.ThreadUtils;

/**
 * The Class Aio. t-io用户关心的API几乎全在这
 *
 * @author tanyaowu
 */
public abstract class Aio {

	/** The log. */
	private static Logger log = LoggerFactory.getLogger(Aio.class);

	/**
	 * 绑定群组
	 * @param channelContext
	 * @param group
	 * @author: tanyaowu
	 */
	public static <SessionContext, P extends Packet, R> void bindGroup(ChannelContext<SessionContext, P, R> channelContext, String group) {
		channelContext.getGroupContext().groups.bind(group, channelContext);
	}

	/**
	 * 绑定用户
	 * @param channelContext
	 * @param userid
	 * @author: tanyaowu
	 */
	public static <SessionContext, P extends Packet, R> void bindUser(ChannelContext<SessionContext, P, R> channelContext, String userid) {
		channelContext.getGroupContext().users.bind(userid, channelContext);
	}

	/**
	 * 关闭连接
	 * @param channelContext
	 * @param remark
	 * @author: tanyaowu
	 */
	public static <SessionContext, P extends Packet, R> void close(ChannelContext<SessionContext, P, R> channelContext, String remark) {
		close(channelContext, null, remark);
	}

	/**
	 * 关闭连接
	 * @param channelContext
	 * @param throwable
	 * @param remark
	 * @author: tanyaowu
	 */
	public static <SessionContext, P extends Packet, R> void close(ChannelContext<SessionContext, P, R> channelContext, Throwable throwable, String remark) {
		close(channelContext, throwable, remark, false);
	}

	/**
	 * 
	 * @param channelContext
	 * @param throwable
	 * @param remark
	 * @param isNeedRemove
	 * @author: tanyaowu
	 */
	private static <SessionContext, P extends Packet, R> void close(ChannelContext<SessionContext, P, R> channelContext, Throwable throwable, String remark, boolean isNeedRemove) {
		if (channelContext.isWaitingClose()) {
			log.info("{} 正在等待被关闭", channelContext);
			return;
		}

		synchronized (channelContext) {
			//double check
			if (channelContext.isWaitingClose()) {
				log.info("{} 正在等待被关闭", channelContext);
				return;
			}
			channelContext.setWaitingClose(true);
			ThreadPoolExecutor closePoolExecutor = channelContext.getGroupContext().getTioExecutor();
			closePoolExecutor.execute(new CloseRunnable<>(channelContext, throwable, remark, isNeedRemove));
		}
	}

	/**
	 * 关闭连接
	 * @param groupContext
	 * @param clientIp
	 * @param clientPort
	 * @param throwable
	 * @param remark
	 * @author: tanyaowu
	 */
	public static <SessionContext, P extends Packet, R> void close(GroupContext<SessionContext, P, R> groupContext, String clientIp, Integer clientPort, Throwable throwable,
			String remark) {
		ChannelContext<SessionContext, P, R> channelContext = groupContext.clientNodes.find(clientIp, clientPort);
		close(channelContext, throwable, remark);
	}

	/**
	 * 根据clientip和clientport获取ChannelContext
	 * @param groupContext
	 * @param clientIp
	 * @param clientPort
	 * @return
	 * @author: tanyaowu
	 */
	public static <SessionContext, P extends Packet, R> ChannelContext<SessionContext, P, R> getChannelContextByClientNode(GroupContext<SessionContext, P, R> groupContext,
			String clientIp, Integer clientPort) {
		return groupContext.clientNodes.find(clientIp, clientPort);
	}

	/**
	 * 根据id获取ChannelContext
	 * @param channelContextId
	 * @return
	 * @author: tanyaowu
	 */
	public static <SessionContext, P extends Packet, R> ChannelContext<SessionContext, P, R> getChannelContextById(GroupContext<SessionContext, P, R> groupContext,
			String channelContextId) {
		return groupContext.ids.find(channelContextId);
	}

	/**
	 * 根据userid获取ChannelContext
	 * @param groupContext
	 * @param userid
	 * @return
	 * @author: tanyaowu
	 */
	public static <SessionContext, P extends Packet, R> ChannelContext<SessionContext, P, R> getChannelContextByUserid(GroupContext<SessionContext, P, R> groupContext,
			String userid) {
		return groupContext.users.find(userid);
	}

	/**
	 * 获取一个组的所有客户端
	 * @param groupContext
	 * @param group
	 * @return
	 * @author: tanyaowu
	 */
	public static <SessionContext, P extends Packet, R> ObjWithLock<Set<ChannelContext<SessionContext, P, R>>> getChannelContextsByGroup(
			GroupContext<SessionContext, P, R> groupContext, String group) {
		return groupContext.groups.clients(group);
	}

	/**
	 * 获取所有连接，包括当前处于断开状态的
	 * @param groupContext
	 * @return
	 * @author: tanyaowu
	 */
	public static <SessionContext, P extends Packet, R> SetWithLock<ChannelContext<SessionContext, P, R>> getAllChannelContexts(GroupContext<SessionContext, P, R> groupContext) {
		return groupContext.connections.getSetWithLock();
	}

	/**
	 * 获取所有处于正常连接状态的连接
	 * @param groupContext
	 * @return
	 * @author: tanyaowu
	 */
	public static <SessionContext, P extends Packet, R> ObjWithLock<Set<ChannelContext<SessionContext, P, R>>> getAllConnectedsChannelContexts(
			GroupContext<SessionContext, P, R> groupContext) {
		return groupContext.connecteds.getSetWithLock();
	}

	/**
	 * 和close方法一样，只不过不再进行重连等维护性的操作
	 * @param channelContext
	 * @param remark
	 * @author: tanyaowu
	 */
	public static <SessionContext, P extends Packet, R> void remove(ChannelContext<SessionContext, P, R> channelContext, String remark) {
		remove(channelContext, null, remark);
	}

	/**
	 * 和close方法一样，只不过不再进行重连等维护性的操作
	 * @param channelContext
	 * @param throwable
	 * @param remark
	 * @author: tanyaowu
	 */
	public static <SessionContext, P extends Packet, R> void remove(ChannelContext<SessionContext, P, R> channelContext, Throwable throwable, String remark) {
		close(channelContext, throwable, remark, true);
	}

	/**
	 * 和close方法一样，只不过不再进行重连等维护性的操作
	 * @param groupContext
	 * @param clientIp
	 * @param clientPort
	 * @param throwable
	 * @param remark
	 * @author: tanyaowu
	 */
	public static <SessionContext, P extends Packet, R> void remove(GroupContext<SessionContext, P, R> groupContext, String clientIp, Integer clientPort, Throwable throwable,
			String remark) {
		ChannelContext<SessionContext, P, R> channelContext = groupContext.clientNodes.find(clientIp, clientPort);
		remove(channelContext, throwable, remark);
	}

	/**
	 * 发送消息到指定ChannelContext
	 * @param channelContext
	 * @param packet
	 * @author: tanyaowu
	 */
	public static <SessionContext, P extends Packet, R> void send(ChannelContext<SessionContext, P, R> channelContext, P packet) {
		send(channelContext, packet, null, null);
	}

	/**
	 * 
	 * @param channelContext
	 * @param packet
	 * @param countDownLatch
	 * @param packetSendMode
	 * @return
	 * @author: tanyaowu
	 */
	private static <SessionContext, P extends Packet, R> Boolean send(final ChannelContext<SessionContext, P, R> channelContext, final P packet, CountDownLatch countDownLatch,
			PacketSendMode packetSendMode) {
		try {
			if (channelContext == null || channelContext.isClosed() || channelContext.isRemoved()) {
				if (countDownLatch != null) {
					countDownLatch.countDown();
				}
				if (channelContext != null) {
					log.error("{}, isClosed:{}, isRemoved:{}, stack:{} ", channelContext, channelContext.isClosed(), channelContext.isRemoved(), ThreadUtils.stackTrace());
				}
				return false;
			}

			boolean isSingleBlock = countDownLatch != null && (packetSendMode == PacketSendMode.SINGLE_BLOCK);

			SendRunnable<SessionContext, P, R> sendRunnable = channelContext.getSendRunnable();
			PacketWithMeta<P> packetWithMeta = null;
			boolean isAdded = false;
			if (countDownLatch == null) {
				isAdded = sendRunnable.addMsg(packet);
			} else {
				packetWithMeta = new PacketWithMeta<>(packet, countDownLatch);
				isAdded = sendRunnable.addMsg(packetWithMeta);
			}

			if (!isAdded) {
				if (countDownLatch != null) {
					countDownLatch.countDown();
				}
				return false;
			}

			//SynThreadPoolExecutor synThreadPoolExecutor = channelContext.getGroupContext().getGroupExecutor();
			channelContext.getGroupContext().getTioExecutor().execute(sendRunnable);

			if (isSingleBlock) {
				long timeout = 10;
				try {
					Boolean awaitFlag = countDownLatch.await(timeout, TimeUnit.SECONDS);
					//log.error("{} after await, packet:{}, countDownLatch:{}", channelContext, packet.logstr(), countDownLatch);

					if (!awaitFlag) {
						log.error("{} 同步发送超时, timeout:{}s, packet:{}", channelContext, timeout, packet.logstr());
					}
				} catch (InterruptedException e) {
					log.error(e.toString(), e);
				}

				Boolean isSentSuccess = packetWithMeta.getIsSentSuccess();
				return isSentSuccess;
			} else {
				return null;
			}
		} catch (Exception e) {
			log.error(e.toString(), e);
			return null;
		} finally {
			//			if (isSingleBlock)
			//			{
			//				org.tio.core.GroupContext.SYN_SEND_SEMAPHORE.release();
			//			}
		}

	}

	/**
	 * 发送到指定的ip和port
	 * @param groupContext
	 * @param ip
	 * @param port
	 * @param packet
	 * @author: tanyaowu
	 */
	public static <SessionContext, P extends Packet, R> void send(GroupContext<SessionContext, P, R> groupContext, String ip, int port, P packet) {
		send(groupContext, ip, port, packet, false);
	}

	/**
	 * 发送到指定的ip和port
	 * @param groupContext
	 * @param ip
	 * @param port
	 * @param packet
	 * @param isBlock
	 * @return
	 * @author: tanyaowu
	 */
	private static <SessionContext, P extends Packet, R> Boolean send(GroupContext<SessionContext, P, R> groupContext, String ip, int port, P packet, boolean isBlock) {
		ChannelContext<SessionContext, P, R> channelContext = groupContext.clientNodes.find(ip, port);
		if (channelContext != null) {
			if (isBlock) {
				return null;
			} else {
				send(channelContext, packet);
				return null;
			}
		} else {
			log.error("can find channelContext by {}:{}", ip, port);
			return false;
		}
	}

	/**
	 * 发消息到所有连接
	 * @param groupContext
	 * @param packet
	 * @param channelContextFilter
	 * @author: tanyaowu
	 */
	public static <SessionContext, P extends Packet, R> void sendToAll(GroupContext<SessionContext, P, R> groupContext, P packet,
			ChannelContextFilter<SessionContext, P, R> channelContextFilter) {
		sendToAll(groupContext, packet, channelContextFilter, false);
	}

	/**
	 * 
	 * @param groupContext
	 * @param packet
	 * @param channelContextFilter
	 * @param isBlock
	 * @author: tanyaowu
	 */
	private static <SessionContext, P extends Packet, R> Boolean sendToAll(GroupContext<SessionContext, P, R> groupContext, P packet,
			ChannelContextFilter<SessionContext, P, R> channelContextFilter, boolean isBlock) {
		ObjWithLock<Set<ChannelContext<SessionContext, P, R>>> setWithLock = groupContext.connections.getSetWithLock();
		if (setWithLock == null) {
			log.debug("没有任何连接");
			return false;
		}

		return sendToSet(groupContext, setWithLock, packet, channelContextFilter, isBlock);
	}

	/**
	 * 发消息到组
	 * @param groupContext
	 * @param group
	 * @param packet
	 * @author: tanyaowu
	 */
	public static <SessionContext, P extends Packet, R> void sendToGroup(GroupContext<SessionContext, P, R> groupContext, String group, P packet) {
		sendToGroup(groupContext, group, packet, null);
	}

	/**
	 * 发消息到组
	 * @param groupContext
	 * @param group
	 * @param packet
	 * @param channelContextFilter
	 * @author: tanyaowu
	 */
	public static <SessionContext, P extends Packet, R> void sendToGroup(GroupContext<SessionContext, P, R> groupContext, String group, P packet,
			ChannelContextFilter<SessionContext, P, R> channelContextFilter) {
		sendToGroup(groupContext, group, packet, channelContextFilter, false);
	}

	/**
	 * 发消息到组
	 * @param groupContext
	 * @param group
	 * @param packet
	 * @param channelContextFilter
	 * @author: tanyaowu
	 */
	private static <SessionContext, P extends Packet, R> Boolean sendToGroup(GroupContext<SessionContext, P, R> groupContext, String group, P packet,
			ChannelContextFilter<SessionContext, P, R> channelContextFilter, boolean isBlock) {
		ObjWithLock<Set<ChannelContext<SessionContext, P, R>>> setWithLock = groupContext.groups.clients(group);
		if (setWithLock == null) {
			log.error("组[{}]不存在", group);
			return false;
		}

		return sendToSet(groupContext, setWithLock, packet, channelContextFilter, isBlock);
	}

	/**
	 * 发消息到指定集合
	 * @param groupContext
	 * @param setWithLock
	 * @param packet
	 * @param channelContextFilter
	 * @author: tanyaowu
	 */
	public static <SessionContext, P extends Packet, R> void sendToSet(GroupContext<SessionContext, P, R> groupContext,
			ObjWithLock<Set<ChannelContext<SessionContext, P, R>>> setWithLock, P packet, ChannelContextFilter<SessionContext, P, R> channelContextFilter) {
		sendToSet(groupContext, setWithLock, packet, channelContextFilter, false);
	}

	/**
	 * 发消息到指定集合
	 * @param groupContext
	 * @param setWithLock
	 * @param packet
	 * @param channelContextFilter
	 * @param isBlock
	 * @author: tanyaowu
	 */
	private static <SessionContext, P extends Packet, R> Boolean sendToSet(GroupContext<SessionContext, P, R> groupContext,
			ObjWithLock<Set<ChannelContext<SessionContext, P, R>>> setWithLock, P packet, ChannelContextFilter<SessionContext, P, R> channelContextFilter, boolean isBlock) {
		//		if (isBlock)
		//		{
		//			try
		//			{
		//				org.tio.core.GroupContext.SYN_SEND_SEMAPHORE.acquire();
		//			} catch (InterruptedException e)
		//			{
		//				log.error(e.toString(), e);
		//			}
		//		}

		Lock lock = setWithLock.getLock().readLock();
		boolean releasedLock = false;
		try {
			lock.lock();
			Set<ChannelContext<SessionContext, P, R>> set = setWithLock.getObj();
			if (set.size() == 0) {
				log.debug("集合为空");
				return false;
			}
			if (!groupContext.isEncodeCareWithChannelContext()) {
				ByteBuffer byteBuffer = groupContext.getAioHandler().encode(packet, groupContext, null);
				packet.setPreEncodedByteBuffer(byteBuffer);
			}

			CountDownLatch countDownLatch = null;
			if (isBlock) {
				countDownLatch = new CountDownLatch(set.size());
			}
			int sendCount = 0;
			for (ChannelContext<SessionContext, P, R> channelContext : set) {
				if (channelContextFilter != null) {
					boolean isfilter = channelContextFilter.filter(channelContext);
					if (!isfilter) {
						if (isBlock) {
							countDownLatch.countDown();
						}
						continue;
					}
				}

				sendCount++;
				if (isBlock) {
					send(channelContext, packet, countDownLatch, PacketSendMode.GROUP_BLOCK);
				} else {
					send(channelContext, packet, null, null);
				}
			}
			lock.unlock();
			releasedLock = true;

			if (sendCount == 0) {
				return false;
			}

			if (isBlock) {
				try {
					long timeout = sendCount / 5;
					timeout = timeout < 10 ? 10 : timeout;
					boolean awaitFlag = countDownLatch.await(timeout, TimeUnit.SECONDS);
					if (!awaitFlag) {
						log.error("同步群发超时, size:{}, timeout:{}, packet:{}", setWithLock.getObj().size(), timeout, packet.logstr());
						return false;
					} else {
						return true;
					}
				} catch (InterruptedException e) {
					log.error(e.toString(), e);
					return false;
				} finally {

				}
			} else {
				return null;
			}
		} catch (Exception e) {
			log.error(e.toString(), e);
			return false;
		} finally {
			//			if (isBlock)
			//			{
			//				org.tio.core.GroupContext.SYN_SEND_SEMAPHORE.release();
			//			}
			if (!releasedLock) {
				lock.unlock();
			}
		}
	}

	/**
	 * 发消息给指定用户
	 * @param groupContext
	 * @param userid
	 * @param packet
	 * @author: tanyaowu
	 */
	public static <SessionContext, P extends Packet, R> void sendToUser(GroupContext<SessionContext, P, R> groupContext, String userid, P packet) {
		sendToUser(groupContext, userid, packet, false);
	}

	/**
	 * 发消息给指定用户
	 * @param groupContext
	 * @param userid
	 * @param packet
	 * @param isBlock
	 * @author: tanyaowu
	 */
	private static <SessionContext, P extends Packet, R> Boolean sendToUser(GroupContext<SessionContext, P, R> groupContext, String userid, P packet, boolean isBlock) {
		ChannelContext<SessionContext, P, R> channelContext = groupContext.users.find(userid);
		if (isBlock) {
			return null;
		} else {
			send(channelContext, packet);
			return null;
		}
	}

	/**
	 * 发消息给指定ChannelContext id
	 * @param channelContextId
	 * @param packet
	 * @author: tanyaowu
	 */
	public static <SessionContext, P extends Packet, R> void sendToId(GroupContext<SessionContext, P, R> groupContext, String channelContextId, P packet) {
		sendToId(groupContext, channelContextId, packet, false);
	}

	/**
	 * 发消息给指定ChannelContext id
	 * @param channelContextId
	 * @param packet
	 * @param isBlock
	 * @return
	 * @author: tanyaowu
	 */
	private static <SessionContext, P extends Packet, R> Boolean sendToId(GroupContext<SessionContext, P, R> groupContext, String channelContextId, P packet, boolean isBlock) {
		ChannelContext<SessionContext, P, R> channelContext = (ChannelContext<SessionContext, P, R>) Aio.getChannelContextById(groupContext, channelContextId);
		if (isBlock) {
			return null;
		} else {
			send(channelContext, packet);
			return null;
		}
	}

	/**
	 * 与所有组解除解绑关系
	 * @param channelContext
	 * @author: tanyaowu
	 */
	public static <SessionContext, P extends Packet, R> void unbindGroup(ChannelContext<SessionContext, P, R> channelContext) {
		channelContext.getGroupContext().groups.unbind(channelContext);
	}

	/**
	 * 与指定组解除绑定关系
	 * @param group
	 * @param channelContext
	 * @author: tanyaowu
	 */
	public static <SessionContext, P extends Packet, R> void unbindGroup(String group, ChannelContext<SessionContext, P, R> channelContext) {
		channelContext.getGroupContext().groups.unbind(group, channelContext);
	}

	/**
	 * 解绑用户
	 * @param channelContext
	 * @author: tanyaowu
	 */
	public static <SessionContext, P extends Packet, R> void unbindUser(ChannelContext<SessionContext, P, R> channelContext) {
		channelContext.getGroupContext().users.unbind(channelContext);
	}

}
