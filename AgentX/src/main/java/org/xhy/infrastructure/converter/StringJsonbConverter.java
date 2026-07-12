package org.xhy.infrastructure.converter;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.postgresql.util.PGobject;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** String ↔ PostgreSQL jsonb 转换器。
 *
 * <p>用于 entity 字段是 {@code String} 但 DB 列是 {@code jsonb} 的场景——
 * 典型如 {@code MessageEntity.metadata}：Java 端存的是已序列化的 JSON 字符串
 * （{@code om.writeValueAsString(meta)}），DB 端要的是 jsonb。
 * </p>
 *
 * <p>为什么不用 {@link JsonToStringConverter}：它是参数化的，编译期需要确定目标类型；
 * 而 metadata 字段就是 String，不应该被再次序列化（再序列化得到的是 JSON-in-JSON 双层引号）。
 * </p>
 *
 * <p>写入：原样 String 包成 PGobject(type=jsonb)。
 * 读取：直接从 jsonb 读 String（PG 返回的就是 JSON 文本，去掉外层引号由 PG 完成）。</p> */
@MappedJdbcTypes(JdbcType.OTHER)
public class StringJsonbConverter extends BaseTypeHandler<String> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType) throws SQLException {
        PGobject jsonObject = new PGobject();
        jsonObject.setType("jsonb");
        jsonObject.setValue(parameter);
        ps.setObject(i, jsonObject);
    }

    @Override
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return rs.getString(columnName);
    }

    @Override
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return rs.getString(columnIndex);
    }

    @Override
    public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return cs.getString(columnIndex);
    }
}
