package me.ly.tools.mybatis.base;

import me.ly.tools.mybatis.entity.Pagination;

import java.util.List;

/**
 * 基础业务接口
 *
 * @author Created by LiYao on 2017-03-03 22:37.
 */
@SuppressWarnings({ "unused", "SameParameterValue" })
public interface BaseService {

    /**
     * 根据条件查询一条
     *
     * @param clazz 对象class
     * @param where where条件，支持?占位符
     * @param params 参数值。与?占位符一一对应
     * @return 具体对象
     */
    <T> T selectOne(Class<T> clazz, String where, Object... params);

    /**
     * 根据条件查询全部
     *
     * @param clazz 对象class
     * @param where where条件，支持?占位符
     * @param params 参数值。与?占位符一一对应
     * @return 具体对象集合
     */
    <T> List<T> selectAll(Class<T> clazz, String where, Object... params);

    /**
     * 根据ID查询
     *
     * @param clazz 对象class
     * @param id id
     * @return 具体对象
     */
    <T> T selectById(Class<T> clazz, Object id);

    /**
     * 根据条件查询全部（分页）
     *
     * @param clazz 实体类对象
     * @param pagination 分页组件
     * @param where 查询条件，支持?占位符
     * @param params 参数值。与?占位符一一对应
     * @return 具体对象集合
     */
    <T> List<T> selectByPage(Class<T> clazz, Pagination pagination, String where, Object... params);

    /**
     * 插入一条数据
     *
     * @param obj 插入对象
     * @return int
     */
    <T> int insert(T obj);

    /**
     * 插入多条数据,目前只支持Mysql数据库
     *
     * @param list 数据集合
     * @return int
     */
    <T> int insertList(List<T> list);

    /**
     * 删除一个实体类。必须设置ID
     *
     * @param obj 需要删除的对象
     */
    <T> int delete(T obj);

    /**
     * 更新一个实体（null或""字段除外），根据ID更新。 <br>
     * ignoreNull = true<br>
     * ignoreEmpty = true
     *
     * @param obj 需修改的对象
     * @return int
     */
    <T> int update(T obj);

    /**
     * 更新一个实体,根据ID更新。 <br>
     *
     * @param obj 带ID的对象
     * @param ignoreNull 是否忽略Null字段
     * @param ignoreEmpty 是否忽略""字段
     * @return int
     */
    <T> int update(T obj, boolean ignoreNull, boolean ignoreEmpty);

    /**
     * 更新多条数据（null或""字段除外）,根据ID更新。
     *
     * @param objs 对象集合
     * @return int
     */
    <T> int updateList(List<T> objs);

    /**
     * 更新多条数据,根据ID更新。 <br>
     *
     * @param objs 带ID的对象集
     * @param ignoreNull 是否忽略Null字段
     * @param ignoreEmpty 是否忽略""字段
     * @return int
     */
    <T> int updateList(List<T> objs, boolean ignoreNull, boolean ignoreEmpty);

    /**
     * 执行一个insert/update/delete SQL语句。不支持SELECT<br>
     *
     * @param sql 支持?占位符
     * @param param 参数值。与?占位符一一对应
     * @return int
     */
    int executeCUD(String sql, Object... param);
}
