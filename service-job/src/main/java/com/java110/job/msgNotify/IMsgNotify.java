package com.java110.job.msgNotify;

import com.alibaba.fastjson.JSONObject;
import com.java110.vo.ResultVo;

/**
 * 消息通知 接口类
 */
public interface IMsgNotify {

    /**
     * 发送退费申请 消息
     *
     * @param userId
     * @param content
     * @return
     */
    ResultVo sendApplyReturnFeeMsg(String communityId, String userId, JSONObject content);

    /**
     * 发送欠费 账单信息
     *
     * @param communityId 小区
     * @param userId 用户
     * @param content {
     *                    "feeTypeName",
     *                     "payerObjName",
     *                     "billAmountOwed",
     *                     "date",
     *                url
     * }
     * @return
     */
    ResultVo sendOweFeeMsg(String communityId, String userId, JSONObject content);

    /**
     * 发送缴费成功提醒
     *
     * @param communityId 小区
     * @param userId 用户
     * @param content {
     *                    "payFeeRoom",
     *                     "feeTypeCdName",
     *                     "payFeeTime",
     *                     "receivedAmount",
     *                url
     * }
     */
    ResultVo sendPayFeeMsg(String communityId, String userId, JSONObject content,String role);

    /**
     * 业主报修时
     * @param communityId 小区
     * @param userId 用户
     * @param content {
     *                repairTypeName，
     *                repairObjName，
     *                repairName，
     *                url
     * }
     * @return
     */
    ResultVo sendAddOwnerRepairMsg(String communityId, String userId, JSONObject content);

    /**
     * 派单给维修师傅
     *
     * @param communityId 小区
     * @param userId      用户
     * @param content     {
     *                    repairName，
     *                    tel，
     *                    time，
     *                    address
     *                    }
     * @return
     */
    ResultVo sendDistributeRepairStaffMsg(String communityId, String userId, JSONObject content);

    /**
     * 派单给业主通知
     *
     * @param communityId 小区
     * @param userId      用户
     * @param content     {
     *                    name，
     *                    tel，
     *                    time，
     *                    url
     *                    }
     * @return
     */
    ResultVo sendDistributeRepairOwnerMsg(String communityId, String userId, JSONObject content);

    /**
     * 报修完成给业主通知
     *
     * @param communityId 小区
     * @param userId      用户
     * @param content     {
     *                    repairObjName，
     *                    staffName，
     *                    time，
     *                    url
     *                    }
     * @return
     */
    ResultVo sendFinishRepairOwnerMsg(String communityId, String userId, JSONObject content);

    /**
     * 退单给业主发送消息
     *
     * @param communityId 小区
     * @param userId      用户
     * @param content     {
     *                    repairTypeName，
     *                    repairObjName，
     *                    repairName，
     *                    url
     *                    }
     * @return
     */
    ResultVo sendReturnRepairMsg(String communityId, String userId, JSONObject content);
}