package com.nopkg.hellodoc.services;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.List;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SearchIndexBackfillTest {
    @Test void unavailableExtensionDoesNotPreventBackfill() throws Exception {
        var jdbc = mock(JdbcTemplate.class);
        var dataSource = mock(DataSource.class);
        var connection = mock(Connection.class);
        var metadata = mock(DatabaseMetaData.class);
        var search = mock(SearchService.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getDatabaseProductName()).thenReturn("PostgreSQL");
        doThrow(new org.springframework.dao.PermissionDeniedDataAccessException("no extension permission", null))
                .when(jdbc).execute("CREATE EXTENSION IF NOT EXISTS pg_trgm");
        when(jdbc.queryForList(anyString(), eq(Long.class), anyLong())).thenReturn(List.of(12L), List.of());
        new SearchIndexBackfill(jdbc, dataSource, search).backfill();
        verify(search).updateIndexSynchronously(12L);
    }
}
