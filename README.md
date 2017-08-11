# 对Mybatis通用 CRUD 封装
将基础的 增、删、改、查进行了封装。省掉大部分简单Mapper的编写，节省开发时间。
## 配置
很简单的几步配置。主要是在`SqlSessionFactoryBean`中加入结果集处理插件和分页插件，以及`MapperScannerConfigurer`中增加`BaseMappper`
```$xml
省略部分代码......

<!--配置插件-->
<bean id="myBatisResultIntercept" class="com.bbd.wtyh.core.mybatis.MybatisResultInterceptor"/>
<bean id="mybatisPaginationInterceptor" class="com.bbd.wtyh.core.mybatis.MybatisPaginationInterceptor"/>

<bean id="sqlSessionFactory" class="org.mybatis.spring.SqlSessionFactoryBean">
    <property name="dataSource" ref="dataSource"/>
    <property name="plugins">
        <list>
            <ref bean="mybatisPaginationInterceptor"/>
            <ref bean="myBatisResultIntercept"/>
        </list>
    </property>
</bean>
<!-- MapperScanner 中加上 BaseMapper -->
<bean class="org.mybatis.spring.mapper.MapperScannerConfigurer">
    <property name="basePackage" value="......,com.bbd.wtyh.core.dao"/>
    <property name="sqlSessionFactoryBeanName" value="sqlSessionFactory"/>
</bean>

省略部分代码......
```
## 代码片段
### 1、BaseService
BaseService 提供了基础增、删、改、查方法，供其它地方使用
```$java
省略部分代码......
<T> T selectOne(Class<T> clazz, String where, Object... params);

<T> List<T> selectAll(Class<T> clazz, String where, Object... params);

<T> T selectById(Class<T> clazz, Object id);

<T> List<T> selectByPage(Class<T> clazz, Pagination pagination, String where, Object... params);

<T> int insert(T obj);

<T> int insertList(List<T> list);

<T> int delete(T obj);

<T> int update(T obj);

<T> int update(T obj, boolean ignoreNull, boolean ignoreEmpty);

<T> int updateList(List<T> objs);

<T> int updateList(List<T> objs, boolean ignoreNull, boolean ignoreEmpty);

int executeCUD(String sql, Object... param);

省略部分代码......
```
### 2、CRUDTemplate
用于构造SQL，配合`BaseMapper`使用。
```$java
省略部分代码......

public String selectById(Map<?, ?> map) {
    SQL sql = new SQL();
    Class<?> clazz = (Class<?>) map.get("clazz");
    sql.SELECT("*").FROM(ReflectUtil.tableName(clazz));
    Object id = map.get("id");
    if (null == id || "".equals(id)) {
        return sql.toString();
    }
    Map<String, String> columnMap = ReflectUtil.id(clazz);
    sql.WHERE(columnMap.get(MyBatisUtil.KEY_ID_COLUMN) + " = #{id}");
    return sql.toString();
}
省略部分代码......
```
### 3、BaseMapper
通过`@SelectProvider`/`@InsertProvider`/`@DeleteProvider`/`@UpdateProvider` 将`CRUDTemplate`构建的SQL交由Mybatis做后续处理
```$java
省略部分代码......

@SelectProvider(type = CRUDTemplate.class, method = "selectById")
<T> List<T> baseSelectById(@Param("clazz") Class<T> clazz, @Param("id") Object id);

省略部分代码......
```
### 4、MybatisResultInterceptor
用于处理查询结果集的拦截器。通过`JPA`注解将POJO字段同数据库中字段对应，再交由该拦截器处理。可以省掉很多ResultMap的编写。  
当然这玩意目前仅支持单表单实体，功能还不是很完善，待后续慢慢完善。
```
......
List<Object> list = new ArrayList<>();
while (rs.next()) {
    Object obj = pojoClazz.newInstance();
    for (Field f : fieldList) {
        f.setAccessible(true);
        if (!f.isAnnotationPresent(Id.class) && !f.isAnnotationPresent(Column.class)) {
            continue;
        }
        Type clazz = f.getGenericType();
        Object o;
        String fieldName = f.getName();
        if (null != f.getAnnotation(Column.class)) {
            Column col = f.getAnnotation(Column.class);
            String cn = col.name();
            if (cn.trim().length() > 0) {
                fieldName = cn;
            }
        }
        if (clazz.equals(int.class) || clazz.equals(Integer.class)) {
            o = rs.getInt(fieldName);
        } else if (clazz.equals(Date.class)) {
            o = rs.getTimestamp(fieldName);
        } else if (clazz.equals(boolean.class) || clazz.equals(Boolean.class)) {
            o = rs.getBoolean(fieldName);
        } else if (clazz.equals(byte.class) || clazz.equals(Byte.class)) {
            o = rs.getByte(fieldName);
        } else {
            o = rs.getObject(fieldName);
        }
        ReflectUtil.setFieldValue(obj, f.getName(), o);
    }
    list.add(obj);
}
......
```
### 5、MybatisPaginationInterceptor
分页拦截器。`SELECT`语句且参数带`Pagination`的方法都会被拦截，拦截后会对SQL做一些包装，比如加上`Limit`
```$java
省略部分代码......

int fromIndex = originalSql.toUpperCase().indexOf("FROM");
String countSql = "SELECT COUNT(*) AS TOTAL " + originalSql.substring(fromIndex);
preparedStatement = connection.prepareStatement(countSql);

if (countSql.contains("?") && paramObj instanceof Map<?, ?>) {
    Map<?, ?> paramMap = (Map<?, ?>) paramObj;
    Object[] params = (Object[]) paramMap.get("params");
    if (params != null) {
        for (int i = 0; i < params.length; i++) {
            preparedStatement.setObject(i + 1, params[i]);
        }
    }

}
rs = preparedStatement.executeQuery();
if (rs.next()) {
    int totalRecord = rs.getInt("TOTAL");
    page.setTotalCount(totalRecord);
}
省略部分代码......

private String mysqlLimitPageSql(Pagination page, String sql) {
    StringBuilder sqlBuilder = new StringBuilder(sql);
    int offset = (page.getPageIndex() - 1) * page.getPageSize();
    sqlBuilder.append(" LIMIT ").append(offset).append(",").append(page.getPageSize());
    return sqlBuilder.toString();
}

省略部分代码......
```
## 使用
### 1、POJO添加注解
这里以区域表作为例子。下面贴出area表及其对应实体类。

|area_id|name|parent_id|province_id|city_id|level|create_by|create_date|
|:-------:|:-------:|:-------:|:-------:|:-------:|:-------:|:-------:|:-------:|
|23|四川|-1|23| |1|system|2016-08-24|
|286|成都|23|23|286|2|system|2016-08-24|
|287|自贡|23|23|287|2|system|2016-08-24|
|288|攀枝花|23|23|288|2|system|2016-08-24|
|289|泸州|23|23|289|2|system|2016-08-24|


```$java

@Table(name = "area")
public class AreaDO extends BaseDO {

    省略部分代码......
    
    @Id
    @Column(name = "area_id")
    private Integer areaId;

    @Column
    private String name;

    @Column(name = "parent_id")
    private Integer parentId;

    @Column(name = "city_id")
    private Integer cityId;

    @Column(name = "province_id")
    private Integer provinceId;
    
    省略getter/setter部分代码......
}
```
如果有继承关系，则需要在父类加上`@MappedSuperclass`。如上代码段中AreaDO extends BaseDO 
```$java
@MappedSuperclass
public class BaseDO implements Serializable {
    
    /** 创建人 */
    @Column(name = "create_by", updatable = false)
    private String createBy;

    /** 创建时间 */
    @Column(name = "create_date", updatable = false)
    private Date createDate;
        
    省略部分代码......
}

```
### 2、实现类继承BaseServiceImpl类
```$java
@Service
public class AreaServiceImpl extends BaseServiceImpl implements AreaService{
    省略部分代码......
    
    public void method(){
        AreaDO areaDo1 = this.selectOne(AreaDO.class,"name = ?","四川");
        System.out.println(JSON.toJSONString(areaDO1));
        //输出：{"areaId":23,"cityId":0,"createBy":"system","createDate":1471968000000,"name":"四川","parentId":-1,"provinceId":23}
    
        AreaDO areaDO2 = this.selectById(AreaDO.class, 23);
        System.out.println(JSON.toJSONString(areaDO2));
        //输出：{"areaId":23,"cityId":0,"createBy":"system","createDate":1471968000000,"name":"四川","parentId":-1,"provinceId":23}
    
        List<AreaDO> areaList = this.selectAll(AreaDO.class, "parent_id = ? LIMIT 2", 23);
        System.out.println(JSON.toJSONString(areaList));
        //输出：
        //  [
        //    {"areaId":286,"cityId":286,"createBy":"system","createDate":1471968000000,"name":"成都","parentId":23,"provinceId":23},
        //    {"areaId":287,"cityId":287,"createBy":"system","createDate":1471968000000,"name":"自贡","parentId":23,"provinceId":23}
        //  ]
    }    
}
```
### 3、注入BaseService
因为可能被其它类继承，所提这里注入时要限定名字。
```$java
@Service
public class AreaServiceImpl implements AreaService{
    @Autowired
    @Qualifier(value = "baseServiceImpl")
    private BaseService baseService;

    public void method(){
        AreaDO areaDo1 = this.baseService.selectOne(AreaDO.class,"name = ?","四川");
        System.out.println(JSON.toJSONString(areaDO1));
        //输出：{"areaId":23,"cityId":0,"createBy":"system","createDate":1471968000000,"name":"四川","parentId":-1,"provinceId":23}
    
        AreaDO areaDO2 = this.baseService.selectById(AreaDO.class, 23);
        System.out.println(JSON.toJSONString(areaDO2));
        //输出：{"areaId":23,"cityId":0,"createBy":"system","createDate":1471968000000,"name":"四川","parentId":-1,"provinceId":23}
    
        List<AreaDO> areaList = this.baseService.selectAll(AreaDO.class, "parent_id = ? LIMIT 2", 23);
        System.out.println(JSON.toJSONString(areaList));
        //输出：
        //  [
        //    {"areaId":286,"cityId":286,"createBy":"system","createDate":1471968000000,"name":"成都","parentId":23,"provinceId":23},
        //    {"areaId":287,"cityId":287,"createBy":"system","createDate":1471968000000,"name":"自贡","parentId":23,"provinceId":23}
        //  ]
    }

}
```