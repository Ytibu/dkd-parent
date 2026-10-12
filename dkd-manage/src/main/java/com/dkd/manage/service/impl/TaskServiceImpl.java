package com.dkd.manage.service.impl;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.dkd.common.constant.DkdContants;
import com.dkd.common.exception.ServiceException;
import com.dkd.common.utils.DateUtils;
import com.dkd.manage.domain.Emp;
import com.dkd.manage.domain.TaskDetails;
import com.dkd.manage.domain.VendingMachine;
import com.dkd.manage.domain.dto.TaskDTO;
import com.dkd.manage.domain.dto.TaskDetailsDTO;
import com.dkd.manage.domain.vo.TaskVO;
import com.dkd.manage.service.IEmpService;
import com.dkd.manage.service.ITaskDetailsService;
import com.dkd.manage.service.IVendingMachineService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import com.dkd.manage.mapper.TaskMapper;
import com.dkd.manage.domain.Task;
import com.dkd.manage.service.ITaskService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.View;

/**
 * 工单Service业务层处理
 * 
 * @author ruoyi
 * @date 2026-10-11
 */
@Service
public class TaskServiceImpl implements ITaskService 
{
    @Autowired
    private TaskMapper taskMapper;
    @Autowired
    private View error;
    @Autowired
    private IEmpService empService;
    @Autowired
    private RedisTemplate redisTemplate;
    @Autowired
    private ITaskDetailsService taskDetailsService;
    @Autowired
    private IVendingMachineService vendingMachineService;

    /**
     * 查询工单
     * 
     * @param taskId 工单主键
     * @return 工单
     */
    @Override
    public Task selectTaskByTaskId(Long taskId)
    {
        return taskMapper.selectTaskByTaskId(taskId);
    }

    /**
     * 查询工单列表
     * 
     * @param task 工单
     * @return 工单
     */
    @Override
    public List<Task> selectTaskList(Task task)
    {
        return taskMapper.selectTaskList(task);
    }

    /**
     * 新增工单
     * 
     * @param task 工单
     * @return 结果
     */
    @Override
    public int insertTask(Task task)
    {
        task.setCreateTime(DateUtils.getNowDate());
        return taskMapper.insertTask(task);
    }

    /**
     * 修改工单
     * 
     * @param task 工单
     * @return 结果
     */
    @Override
    public int updateTask(Task task)
    {
        task.setUpdateTime(DateUtils.getNowDate());
        return taskMapper.updateTask(task);
    }

    /**
     * 批量删除工单
     * 
     * @param taskIds 需要删除的工单主键
     * @return 结果
     */
    @Override
    public int deleteTaskByTaskIds(Long[] taskIds)
    {
        return taskMapper.deleteTaskByTaskIds(taskIds);
    }

    /**
     * 删除工单信息
     * 
     * @param taskId 工单主键
     * @return 结果
     */
    @Override
    public int deleteTaskByTaskId(Long taskId)
    {
        return taskMapper.deleteTaskByTaskId(taskId);
    }

    @Override
    public List<TaskVO> selectTaskVOList(Task task)
    {
        return taskMapper.selectTaskVOList(task);
    }

    /**
     * 新增运营工单或运维工单
     */
    @Transactional
    @Override
    public int inertTaskDTO(TaskDTO taskDTO)
    {
        VendingMachine vendingMachine = vendingMachineService.selectVendingMachineByInnerCode(taskDTO.getInnerCode());
        if(vendingMachine==null){
            throw new ServiceException("设备不存在");
        }

        checkCreateTask(vendingMachine.getVmStatus(), taskDTO.getProductTypeId());

        hasTask(taskDTO);

        Emp emp = empService.selectEmpById(taskDTO.getUserId());
        if(emp == null){
            throw new ServiceException("员工不存在");
        }

        if(!emp.getRegionId().equals(vendingMachine.getRegionId())){
            throw new ServiceException("员工区域与设备区域不一致，无法处理此工单");
        }

        Task task = BeanUtil.copyProperties(taskDTO, Task.class);
        task.setTaskStatus(DkdContants.TASK_STATUS_CREATE);
        task.setUserName(emp.getUserName());
        task.setRegionId(vendingMachine.getRegionId());
        task.setAddr(vendingMachine.getAddr());
        task.setCreateTime(DateUtils.getNowDate());
        task.setTaskCode(generateTaskCode());
        int i = taskMapper.insertTask(task);
        if(taskDTO.getProductTypeId().equals(DkdContants.TASK_TYPE_SUPPLY)){
            List<TaskDetailsDTO> details = taskDTO.getDetails();
            if(CollUtil.isEmpty(details)){
                throw new ServiceException("补货工单详情不能为空");
            }
            List<TaskDetails> collect = details.stream().map(
                    detail -> {
                        TaskDetails taskDetails = BeanUtil.copyProperties(detail, TaskDetails.class);
                        taskDetails.setTaskId(task.getTaskId());
                        return taskDetails;
                    }
            ).collect(Collectors.toList());
            taskDetailsService.insertTaskDetailsBatch(collect);
        }

        return i;
    }

    @Override
    public int cancelTask(Task task)
    {
        Task taskDb = taskMapper.selectTaskByTaskId(task.getTaskId());
        if(taskDb.getTaskStatus().equals(DkdContants.TASK_STATUS_CANCEL)){
            throw new ServiceException("该工单已经取消，无法再次取消");
        }

        if(taskDb.getTaskStatus().equals(DkdContants.TASK_STATUS_FINISH)){
            throw new ServiceException("该工单已经完成，无法取消");
        }
        task.setTaskStatus(DkdContants.TASK_STATUS_CANCEL);
        task.setUpdateTime(DateUtils.getNowDate());
        return taskMapper.updateTask(task);
    }

    // 生成并获取当日工单编号
    private String generateTaskCode()
    {
        String dataStr = DateUtils.getDate().replaceAll("-", "");
        String key = "dkd.task.code." +dataStr;
        if(!redisTemplate.hasKey(key)){
            redisTemplate.opsForValue().set(key, 1, Duration.ofDays(1));
            return dataStr + "0001";
        }

        return dataStr + StrUtil.padPre(Objects.requireNonNull(redisTemplate.opsForValue().increment(key)).toString(), 4, '0');
    }

    private void hasTask(TaskDTO taskDTO)
    {
        Task task = new Task();
        task.setInnerCode(taskDTO.getInnerCode());
        task.setProductTypeId(taskDTO.getProductTypeId());
        task.setTaskStatus(DkdContants.TASK_STATUS_PROGRESS);
        List<Task> tasks = taskMapper.selectTaskList(task);
        if(!tasks.isEmpty()){
            throw new ServiceException("该设备存在未完成工单，不能重复创建");
        }
    }

    private void checkCreateTask(Long vmStatus, Long productTypeId)
    {
        if(Objects.equals(productTypeId, DkdContants.TASK_TYPE_DEPLOY) && Objects.equals(vmStatus, DkdContants.VM_STATUS_RUNNING)){
            throw new ServiceException("设备正在运行，无法投放");
        }
        if(Objects.equals(productTypeId, DkdContants.TASK_TYPE_REPAIR) && !Objects.equals(vmStatus, DkdContants.VM_STATUS_RUNNING)){
            throw new ServiceException("该设备未运行，无法维修");
        }
        if(Objects.equals(productTypeId, DkdContants.TASK_TYPE_SUPPLY) && !Objects.equals(vmStatus, DkdContants.VM_STATUS_RUNNING)){
            throw new ServiceException("该设备未运行，无法补货");
        }
        if(Objects.equals(productTypeId, DkdContants.TASK_TYPE_REVOKE) && !Objects.equals(vmStatus, DkdContants.VM_STATUS_RUNNING)){
            throw new ServiceException("该设备未运行，无法撤机");
        }
    }
}
