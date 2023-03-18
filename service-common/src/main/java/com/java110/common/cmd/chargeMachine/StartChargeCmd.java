package com.java110.common.cmd.chargeMachine;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.java110.common.charge.IChargeCore;
import com.java110.core.annotation.Java110Cmd;
import com.java110.core.annotation.Java110Transactional;
import com.java110.core.context.ICmdDataFlowContext;
import com.java110.core.event.cmd.Cmd;
import com.java110.core.event.cmd.CmdEvent;
import com.java110.core.factory.GenerateCodeFactory;
import com.java110.dto.account.AccountDto;
import com.java110.dto.accountDetail.AccountDetailDto;
import com.java110.dto.chargeMachine.ChargeMachineDto;
import com.java110.dto.chargeMachineOrder.ChargeMachineOrderDto;
import com.java110.dto.chargeMachinePort.ChargeMachinePortDto;
import com.java110.dto.chargeRuleFee.ChargeRuleFeeDto;
import com.java110.dto.couponPropertyPoolConfig.CouponPropertyPoolConfigDto;
import com.java110.dto.couponPropertyUser.CouponPropertyUserDto;
import com.java110.dto.user.UserDto;
import com.java110.intf.acct.IAccountInnerServiceSMO;
import com.java110.intf.acct.ICouponPropertyPoolConfigV1InnerServiceSMO;
import com.java110.intf.acct.ICouponPropertyUserDetailV1InnerServiceSMO;
import com.java110.intf.acct.ICouponPropertyUserV1InnerServiceSMO;
import com.java110.intf.common.*;
import com.java110.intf.user.IUserV1InnerServiceSMO;
import com.java110.po.accountDetail.AccountDetailPo;
import com.java110.po.chargeMachineOrder.ChargeMachineOrderPo;
import com.java110.po.chargeMachineOrderAcct.ChargeMachineOrderAcctPo;
import com.java110.po.chargeMachinePort.ChargeMachinePortPo;
import com.java110.po.couponPropertyUser.CouponPropertyUserPo;
import com.java110.utils.exception.CmdException;
import com.java110.utils.lock.DistributedLock;
import com.java110.utils.util.Assert;
import com.java110.utils.util.DateUtil;
import com.java110.utils.util.StringUtil;
import com.java110.vo.ResultVo;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.text.ParseException;
import java.util.Calendar;
import java.util.List;

/**
 * 充电桩开始充电 处理类
 * <p>
 * add by wuxw 2023-03-09
 */
@Java110Cmd(serviceCode = "chargeMachine.startCharge")
public class StartChargeCmd extends Cmd {

    @Autowired
    private IChargeMachineV1InnerServiceSMO chargeMachineV1InnerServiceSMOImpl;

    @Autowired
    private IChargeMachinePortV1InnerServiceSMO chargeMachinePortV1InnerServiceSMOImpl;

    @Autowired
    private IUserV1InnerServiceSMO userV1InnerServiceSMOImpl;

    @Autowired
    private IAccountInnerServiceSMO accountInnerServiceSMOImpl;

    @Autowired
    private IChargeCore chargeCoreImpl;

    @Autowired
    private IChargeMachineOrderV1InnerServiceSMO chargeMachineOrderV1InnerServiceSMOImpl;

    @Autowired
    private IChargeMachineOrderAcctV1InnerServiceSMO chargeMachineOrderAcctV1InnerServiceSMOImpl;
    @Autowired
    private ICouponPropertyUserV1InnerServiceSMO couponPropertyUserV1InnerServiceSMOImpl;

    @Autowired
    private ICouponPropertyUserDetailV1InnerServiceSMO couponPropertyUserDetailV1InnerServiceSMOImpl;

    @Autowired
    private ICouponPropertyPoolConfigV1InnerServiceSMO couponPropertyPoolConfigV1InnerServiceSMOImpl;

    @Autowired
    private IChargeRuleFeeV1InnerServiceSMO chargeRuleFeeV1InnerServiceSMOImpl;

    @Override
    public void validate(CmdEvent event, ICmdDataFlowContext context, JSONObject reqJson) throws CmdException, ParseException {
        Assert.hasKeyAndValue(reqJson, "communityId", "未包含小区信息");
        Assert.hasKeyAndValue(reqJson, "machineId", "未包含充电桩");
        Assert.hasKeyAndValue(reqJson, "portId", "未包含插槽");
        Assert.hasKeyAndValue(reqJson, "duration", "未包含充电时长"); // 999 为按量充电

        String userId = context.getReqHeaders().get("user-id");
        Assert.hasLength(userId, "用户不存在");

        //查询充电桩设备信息

        ChargeMachineDto chargeMachineDto = new ChargeMachineDto();
        chargeMachineDto.setCommunityId(reqJson.getString("communityId"));
        chargeMachineDto.setMachineId(reqJson.getString("machineId"));
        List<ChargeMachineDto> chargeMachineDtos = chargeMachineV1InnerServiceSMOImpl.queryChargeMachines(chargeMachineDto);

        Assert.listOnlyOne(chargeMachineDtos, "充电桩 不存在");

        // todo 插槽是否空闲

        ChargeMachinePortDto chargeMachinePortDto = new ChargeMachinePortDto();
        chargeMachinePortDto.setMachineId(reqJson.getString("machineId"));
        chargeMachinePortDto.setPortId(reqJson.getString("portId"));
        chargeMachinePortDto.setState(ChargeMachinePortDto.STATE_FREE);
        List<ChargeMachinePortDto> chargeMachinePortDtos = chargeMachinePortV1InnerServiceSMOImpl.queryChargeMachinePorts(chargeMachinePortDto);
        Assert.listOnlyOne(chargeMachinePortDtos, "插槽忙线");


        double duration = reqJson.getDoubleValue("duration");
        if (duration == 999) {
            duration = 10;
        }

        ChargeRuleFeeDto chargeRuleFeeDto = new ChargeRuleFeeDto();
        chargeRuleFeeDto.setRuleId(chargeMachineDtos.get(0).getRuleId());
        chargeRuleFeeDto.setCommunityId(chargeMachineDtos.get(0).getCommunityId());
        List<ChargeRuleFeeDto> chargeRuleFeeDtos = chargeRuleFeeV1InnerServiceSMOImpl.queryChargeRuleFees(chargeRuleFeeDto);

        if (chargeRuleFeeDtos == null || chargeRuleFeeDtos.size() < 1) {
            throw new CmdException("未设置充值收费");
        }
        reqJson.put("durationPrice",chargeRuleFeeDtos.get(chargeRuleFeeDtos.size()-1).getDurationPrice());

        BigDecimal payMoneyDec = new BigDecimal(Double.parseDouble(chargeRuleFeeDtos.get(chargeRuleFeeDtos.size()-1).getDurationPrice()));

        double payMoney = payMoneyDec.multiply(new BigDecimal(duration)).setScale(2, BigDecimal.ROUND_HALF_UP).doubleValue();


        UserDto userDto = new UserDto();
        userDto.setUserId(userId);
        List<UserDto> userDtos = userV1InnerServiceSMOImpl.queryUsers(userDto);

        Assert.listOnlyOne(userDtos, "用户不存在");

        AccountDto accountDto = new AccountDto();
        accountDto.setLink(userDtos.get(0).getTel());
        accountDto.setAcctType(AccountDto.ACCT_TYPE_CASH);
        accountDto.setObjType(AccountDto.OBJ_TYPE_PERSON);
        accountDto.setPartId(reqJson.getString("communityId"));
        List<AccountDto> accountDtos = accountInnerServiceSMOImpl.queryAccounts(accountDto);

        if (accountDtos == null || accountDtos.size() < 1) {
            throw new CmdException("请先充值，账户金额不足");
        }

        if (Double.parseDouble(accountDtos.get(0).getAmount()) < payMoney) {
            throw new CmdException("账户金额不足，无法支付" + duration + "小时费用,请先充值");
        }

        reqJson.put("acctId", accountDtos.get(0).getAcctId());

        if (!reqJson.containsKey("couponIds") || StringUtil.isEmpty(reqJson.getString("couponIds"))) {
            return;
        }

        for (String couponId : reqJson.getString("couponIds").split(",")) {
            CouponPropertyUserDto couponPropertyUserDto = new CouponPropertyUserDto();
            couponPropertyUserDto.setCouponId(couponId);
            couponPropertyUserDto.setToType(CouponPropertyUserDto.TO_TYPE_CHARGE);
            couponPropertyUserDto.setState(CouponPropertyUserDto.STATE_WAIT);

            List<CouponPropertyUserDto> couponPropertyUserDtos = couponPropertyUserV1InnerServiceSMOImpl.queryCouponPropertyUsers(couponPropertyUserDto);

            if (couponPropertyUserDtos == null || couponPropertyUserDtos.size() < 1) {
                throw new CmdException("优惠券不存在");
            }
            if (!"Y".equals(couponPropertyUserDtos.get(0).getIsExpire())) {
                throw new CmdException("优惠券已过期");
            }
        }

    }

    @Override
    @Java110Transactional
    public void doCmd(CmdEvent event, ICmdDataFlowContext context, JSONObject reqJson) throws CmdException, ParseException {
        String userId = context.getReqHeaders().get("user-id");
        UserDto userDto = new UserDto();
        userDto.setUserId(userId);
        List<UserDto> userDtos = userV1InnerServiceSMOImpl.queryUsers(userDto);

        Assert.listOnlyOne(userDtos, "用户不存在");
        ChargeMachineDto chargeMachineDto = new ChargeMachineDto();
        chargeMachineDto.setCommunityId(reqJson.getString("communityId"));
        chargeMachineDto.setMachineId(reqJson.getString("machineId"));
        List<ChargeMachineDto> chargeMachineDtos = chargeMachineV1InnerServiceSMOImpl.queryChargeMachines(chargeMachineDto);
        double duration = reqJson.getDoubleValue("duration");
        String durationType = IChargeCore.CHARGE_TYPE_HOURS;
        double durationHours = duration;
        if (duration == 999) {
            durationType = IChargeCore.CHARGE_TYPE_ENERGY;
            durationHours = 10;
        }


        ChargeMachinePortDto chargeMachinePortDto = new ChargeMachinePortDto();
        chargeMachinePortDto.setMachineId(reqJson.getString("machineId"));
        chargeMachinePortDto.setPortId(reqJson.getString("portId"));
        chargeMachinePortDto.setState(ChargeMachinePortDto.STATE_FREE);
        List<ChargeMachinePortDto> chargeMachinePortDtos = chargeMachinePortV1InnerServiceSMOImpl.queryChargeMachinePorts(chargeMachinePortDto);

        String orderId = GenerateCodeFactory.getGeneratorId("11");
        //调用充电桩充电
        ResultVo resultVo = chargeCoreImpl.startCharge(chargeMachineDtos.get(0), chargeMachinePortDtos.get(0), durationType, durationHours, orderId);

        if (resultVo.getCode() != ResultVo.CODE_OK) {
            context.setResponseEntity(ResultVo.createResponseEntity(resultVo));
            return;
        }

        // todo 修改端口状态
        ChargeMachinePortPo chargeMachinePortPo = new ChargeMachinePortPo();
        chargeMachinePortPo.setPortId(chargeMachinePortDtos.get(0).getPortId());
        chargeMachinePortPo.setCommunityId(chargeMachinePortDtos.get(0).getCommunityId());
        chargeMachinePortPo.setState(ChargeMachinePortDto.STATE_WORKING);
        chargeMachinePortV1InnerServiceSMOImpl.updateChargeMachinePort(chargeMachinePortPo);

        // todo 生成 充电订单
        ChargeMachineOrderPo chargeMachineOrderPo = new ChargeMachineOrderPo();
        chargeMachineOrderPo.setAmount("0");
        chargeMachineOrderPo.setOrderId(orderId);
        chargeMachineOrderPo.setPortId(chargeMachinePortDtos.get(0).getPortId());
        chargeMachineOrderPo.setPersonName(userDtos.get(0).getName());
        chargeMachineOrderPo.setMachineId(chargeMachineDtos.get(0).getMachineId());
        chargeMachineOrderPo.setAcctDetailId(reqJson.getString("acctId"));
        chargeMachineOrderPo.setPersonId(userId);
        chargeMachineOrderPo.setChargeHours(reqJson.getString("duration"));
        chargeMachineOrderPo.setStartTime(DateUtil.getNow(DateUtil.DATE_FORMATE_STRING_A));
        chargeMachineOrderPo.setDurationPrice(reqJson.getString("durationPrice"));
        Calendar calendar = Calendar.getInstance();
        if (duration == 999) {
            calendar.add(Calendar.HOUR, 10);
        } else {
            calendar.add(Calendar.HOUR, reqJson.getIntValue("duration"));
        }

        chargeMachineOrderPo.setEndTime(DateUtil.getFormatTimeString(calendar.getTime(), DateUtil.DATE_FORMATE_STRING_A));
        chargeMachineOrderPo.setState(ChargeMachineOrderDto.STATE_DOING);
        chargeMachineOrderPo.setPersonTel(userDtos.get(0).getTel());
        chargeMachineOrderPo.setCommunityId(chargeMachineDtos.get(0).getCommunityId());
        chargeMachineOrderPo.setEnergy("1");
        int flag = chargeMachineOrderV1InnerServiceSMOImpl.saveChargeMachineOrder(chargeMachineOrderPo);

        if (flag < 1) {
            chargeCoreImpl.stopCharge(chargeMachineDtos.get(0), chargeMachinePortDtos.get(0));
            throw new CmdException("充电失败");
        }
        resultVo.setData(orderId);

        double couponDurationHours = 0.0;
        //扣款
        if (reqJson.containsKey("couponIds") && !StringUtil.isEmpty(reqJson.getString("couponIds"))) {
            //todo 优惠券抵扣
            couponDurationHours = withholdCoupon(reqJson, chargeMachineDtos, orderId);
        }

        if (durationHours - couponDurationHours > 0) {
            // todo 3.0 账户扣款
            withholdAccount(reqJson, chargeMachineDtos, orderId, durationHours, couponDurationHours);
        }

        context.setResponseEntity(ResultVo.createResponseEntity(resultVo));
    }

    /**
     * 优惠券抵扣
     *
     * @param reqJson
     * @param chargeMachineDtos
     * @param orderId
     */
    private double withholdCoupon(JSONObject reqJson, List<ChargeMachineDto> chargeMachineDtos, String orderId) {
        int flag;
        double hours = 0;
        String couponNames = "";
        for (String couponId : reqJson.getString("couponIds").split(",")) {
            String requestId = DistributedLock.getLockUUID();
            String key = this.getClass().getSimpleName() + reqJson.getString("couponId");
            try {
                CouponPropertyUserDto couponPropertyUserDto = new CouponPropertyUserDto();
                couponPropertyUserDto.setCouponId(couponId);
                couponPropertyUserDto.setToType(CouponPropertyUserDto.TO_TYPE_CHARGE);
                couponPropertyUserDto.setState(CouponPropertyUserDto.STATE_WAIT);

                List<CouponPropertyUserDto> couponPropertyUserDtos = couponPropertyUserV1InnerServiceSMOImpl.queryCouponPropertyUsers(couponPropertyUserDto);
                int stock = Integer.parseInt(couponPropertyUserDtos.get(0).getStock());
                CouponPropertyUserPo couponPropertyUserPo = new CouponPropertyUserPo();
                couponPropertyUserPo.setCouponId(couponPropertyUserDtos.get(0).getCouponId());
                couponPropertyUserPo.setCommunityId(couponPropertyUserDtos.get(0).getCommunityId());
                couponPropertyUserPo.setStock((stock - 1) + "");
                if (stock == 1) {
                    couponPropertyUserPo.setState(CouponPropertyUserDto.STATE_FINISH);
                }
                flag = couponPropertyUserV1InnerServiceSMOImpl.updateCouponPropertyUser(couponPropertyUserPo);
                if (flag < 1) {
                    throw new CmdException("核销失败");
                }

                couponNames += ("优惠券名称：" + couponPropertyUserDtos.get(0).getCouponName() + ",优惠券编号：" + couponId + ";");

                CouponPropertyPoolConfigDto couponPropertyPoolConfigDto = new CouponPropertyPoolConfigDto();
                couponPropertyPoolConfigDto.setCouponId(couponPropertyUserDtos.get(0).getCppId());
                couponPropertyPoolConfigDto.setColumnKey("hours");
                List<CouponPropertyPoolConfigDto> couponPropertyPoolConfigDtos = couponPropertyPoolConfigV1InnerServiceSMOImpl.queryCouponPropertyPoolConfigs(couponPropertyPoolConfigDto);

                Assert.listOnlyOne(couponPropertyPoolConfigDtos, "未包含优惠券配置信息");

                double value = Double.parseDouble(couponPropertyPoolConfigDtos.get(0).getColumnValue());
                hours += value;
            } finally {
                DistributedLock.releaseDistributedLock(requestId, key);
            }
        }

        hours = Math.ceil(hours);

        ChargeMachineOrderAcctPo chargeMachineOrderAcctPo = new ChargeMachineOrderAcctPo();
        chargeMachineOrderAcctPo.setAcctDetailId("-1");
        chargeMachineOrderAcctPo.setAmount(reqJson.getString("durationPrice"));
        chargeMachineOrderAcctPo.setCmoaId(GenerateCodeFactory.getGeneratorId("11"));
        chargeMachineOrderAcctPo.setOrderId(orderId);
        chargeMachineOrderAcctPo.setAcctId(reqJson.getString("acctId"));
        chargeMachineOrderAcctPo.setStartTime(DateUtil.getNow(DateUtil.DATE_FORMATE_STRING_A));
        chargeMachineOrderAcctPo.setEndTime(DateUtil.getAddHoursStringA(DateUtil.getCurrentDate(), new Double(hours).intValue()));
        chargeMachineOrderAcctPo.setRemark("优惠券抵扣," + couponNames);
        chargeMachineOrderAcctPo.setCommunityId(chargeMachineDtos.get(0).getCommunityId());
        chargeMachineOrderAcctPo.setEnergy("0");

        chargeMachineOrderAcctV1InnerServiceSMOImpl.saveChargeMachineOrderAcct(chargeMachineOrderAcctPo);

        return hours;
    }

    /**
     * 账户抵扣
     *
     * @param reqJson
     * @param chargeMachineDtos
     * @param orderId
     */
    private void withholdAccount(JSONObject reqJson, List<ChargeMachineDto> chargeMachineDtos, String orderId, double durationHours, double couponDurationHours) {
        AccountDto accountDto = new AccountDto();
        accountDto.setAcctId(reqJson.getString("acctId"));
        List<AccountDto> accountDtos = accountInnerServiceSMOImpl.queryAccounts(accountDto);

        double amount = 0.0;
        BigDecimal durationPrice = new BigDecimal(Double.parseDouble(reqJson.getString("durationPrice")));
        if (couponDurationHours > 0) {
            durationPrice = durationPrice.multiply(new BigDecimal(durationHours - couponDurationHours)).setScale(2, BigDecimal.ROUND_HALF_UP);
            amount = durationPrice.doubleValue();
        } else {
            durationPrice = durationPrice.multiply(new BigDecimal(durationHours)).setScale(2, BigDecimal.ROUND_HALF_UP);
            amount = durationPrice.doubleValue();
        }

        AccountDetailPo accountDetailPo = new AccountDetailPo();
        accountDetailPo.setAcctId(accountDtos.get(0).getAcctId());
        accountDetailPo.setObjId(accountDtos.get(0).getObjId());
        accountDetailPo.setObjType(accountDtos.get(0).getObjType());
        accountDetailPo.setAmount(amount + "");
        accountDetailPo.setDetailId(GenerateCodeFactory.getGeneratorId(GenerateCodeFactory.CODE_PREFIX_detailId));
        accountInnerServiceSMOImpl.withholdAccount(accountDetailPo);
        ChargeMachineOrderAcctPo chargeMachineOrderAcctPo = new ChargeMachineOrderAcctPo();
        chargeMachineOrderAcctPo.setAcctDetailId(accountDetailPo.getDetailId());
        chargeMachineOrderAcctPo.setAmount(amount + "");

        chargeMachineOrderAcctPo.setCmoaId(GenerateCodeFactory.getGeneratorId("11"));
        chargeMachineOrderAcctPo.setOrderId(orderId);
        chargeMachineOrderAcctPo.setAcctId(accountDtos.get(0).getAcctId());
        if (couponDurationHours > 0) {
            chargeMachineOrderAcctPo.setStartTime(DateUtil.getNow(DateUtil.DATE_FORMATE_STRING_A));
        } else {
            chargeMachineOrderAcctPo.setStartTime(DateUtil.getAddHoursStringA(DateUtil.getCurrentDate(), new Double(Math.ceil(couponDurationHours)).intValue()));
        }
        chargeMachineOrderAcctPo.setEndTime(DateUtil.getAddHoursStringA(DateUtil.getCurrentDate(), new Double(Math.ceil(durationHours)).intValue()));
        chargeMachineOrderAcctPo.setRemark("账户扣款");
        chargeMachineOrderAcctPo.setCommunityId(chargeMachineDtos.get(0).getCommunityId());
        chargeMachineOrderAcctPo.setEnergy("0");
        chargeMachineOrderAcctPo.setDurationPrice(reqJson.getString("durationPrice"));


        chargeMachineOrderAcctV1InnerServiceSMOImpl.saveChargeMachineOrderAcct(chargeMachineOrderAcctPo);
    }
}