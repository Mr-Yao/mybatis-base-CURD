package me.ly.tools.mybatis.dao;

import me.ly.tools.mybatis.entity.Pagination;
import me.ly.tools.mybatis.mybatis.CRUDTemplate;
import me.ly.tools.mybatis.mybatis.annotation.ResultIntercept;
import org.apache.ibatis.annotations.*;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 通用 增、删、改、查 Mapper接口
 *
 * @author Created by LiYao on 2017-03-03 22:40.
 */
@Repository
@ResultIntercept
@SuppressWarnings("all")
public interface BaseMapper {

    /**
     * 根据条件查询全部，不支持实体类包含子类的
     *
     * @param clazz 对象类型
     * @param where 查询条件，支持?占位符。
     * @param params 参数值。与?占位符一一对应
     * @return 对象集合
     */
    @SelectProvider(type = CRUDTemplate.class, method = "select")
    <T> List<T> selectAll(@Param("returnTypeClass") Class<T> clazz, @Param("where") String where, @Param("params") Object... params);

    /**
     * 根据ID查询实体类
     *
     * @param clazz
     * @param id
     * @return
     */
    @SelectProvider(type = CRUDTemplate.class, method = "selectById")
    <T> List<T> selectById(@Param("returnTypeClass") Class<T> clazz, @Param("id") Object id);

    /**
     * 根据条件查询全部（分页），实体类包含子类的不支持
     *
     * @param pagination 分页组件
     * @param clazz 实体类对象
     * @param where 查询条件
     * @return
     */
    @SelectProvider(type = CRUDTemplate.class, method = "select")
    <T> List<T> selectByPage(@Param("returnTypeClass") Class<T> clazz, @Param("pagination") Pagination pagination,
            @Param("where") String where, @Param("params") Object... params);

    /**
     * 插入一条数据
     *
     * @param obj
     * @return
     */
    @InsertProvider(type = CRUDTemplate.class, method = "insert")
    @Options(useGeneratedKeys = true)
    <T> int insert(T obj);

    /**
     * 插入多条数据
     *
     * @param list
     * @return
     */
    @InsertProvider(type = CRUDTemplate.class, method = "insertList")
    <T> int insertList(@Param("list") List<T> list);

    /**
     * 删除一个实体类
     *
     * @param obj
     */
    @DeleteProvider(type = CRUDTemplate.class, method = "delete")
    <T> int delete(T obj);

    /**
     * 更新一个实体,根据ID更新。 <br>
     *
     * @param obj 带ID的对象
     * @param ignoreNull 是否忽略Null字段
     * @param ignoreEmpty 是否忽略""字段
     * @return
     */
    @UpdateProvider(type = CRUDTemplate.class, method = "update")
    <T> int update(@Param("bean") T obj, @Param("ignoreNull") boolean ignoreNull, @Param("ignoreEmpty") boolean ignoreEmpty);

    @InsertProvider(type = CRUDTemplate.class, method = "refactorSql")
    int executeInsert(@Param("sql") String sql, @Param("params") Object... param);

    @DeleteProvider(type = CRUDTemplate.class, method = "refactorSql")
    int executeDelete(@Param("sql") String sql, @Param("params") Object... param);

    @UpdateProvider(type = CRUDTemplate.class, method = "refactorSql")
    int executeUpdate(@Param("sql") String sql, @Param("params") Object... param);
}
