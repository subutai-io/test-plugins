package org.safehaus.subutai.plugin.etl.rest;


import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.safehaus.subutai.common.util.JsonUtil;
import org.safehaus.subutai.plugin.etl.api.DataSourceType;
import org.safehaus.subutai.plugin.etl.api.ETLConfig;
import org.safehaus.subutai.plugin.etl.api.Sqoop;
import org.safehaus.subutai.plugin.etl.api.ETLConfig;
import org.safehaus.subutai.plugin.etl.api.setting.ExportSetting;
import org.safehaus.subutai.plugin.etl.api.setting.ImportParameter;
import org.safehaus.subutai.plugin.etl.api.setting.ImportSetting;

import com.google.common.base.Strings;


public class RestService
{

    private static final String OPERATION_ID = "OPERATION_ID";

    private Sqoop sqoopManager;


    public void setSqoopManager( Sqoop sqoopManager )
    {
        this.sqoopManager = sqoopManager;
    }


    @GET
    @Path( "clusters" )
    @Produces( { MediaType.APPLICATION_JSON } )
    public Response getClusters()
    {

        List<ETLConfig> configs = sqoopManager.getClusters();
        ArrayList<String> clusterNames = new ArrayList();

        for ( ETLConfig config : configs )
        {
            clusterNames.add( config.getClusterName() );
        }

        String clusters = JsonUtil.GSON.toJson( clusterNames );
        return Response.status( Response.Status.OK ).entity( clusters ).build();
    }


    @GET
    @Path( "clusters/{clusterName}" )
    @Produces( { MediaType.APPLICATION_JSON } )
    public Response getCluster( @PathParam( "clusterName" ) String clusterName )
    {
        ETLConfig config = sqoopManager.getCluster( clusterName );

        String cluster = JsonUtil.GSON.toJson( config );
        return Response.status( Response.Status.OK ).entity( cluster ).build();
    }


    @POST
    @Path( "clusters" )
    @Produces( { MediaType.APPLICATION_JSON } )
    public Response installCluster( @QueryParam( "config" ) String config )
    {

        ETLConfig sqoopConfig = JsonUtil.fromJson( config, ETLConfig.class );

        UUID uuid = sqoopManager.installCluster( sqoopConfig );

        String operationId = JsonUtil.toJson( OPERATION_ID, uuid );
        return Response.status( Response.Status.CREATED ).entity( operationId ).build();
    }


    @DELETE
    @Path( "clusters/{clusterName}/nodes/{hostname}" )
    @Produces( { MediaType.APPLICATION_JSON } )
    public Response destroyNode( @PathParam( "clusterName" ) String clusterName,
                                 @PathParam( "hostname" ) String hostname )
    {
        UUID uuid = sqoopManager.destroyNode( clusterName, hostname );

        String operationId = JsonUtil.toJson( OPERATION_ID, uuid );
        return Response.status( Response.Status.OK ).entity( operationId ).build();
    }


    @POST
    @Path( "importData" )
    @Produces( { MediaType.APPLICATION_JSON } )
    public Response importData( @QueryParam( "dataSourceType" ) String dataSourceType,
                                @QueryParam( "importAllTables" ) String importAllTables,
                                @QueryParam( "datasourceDatabase" ) String datasourceDatabase,
                                @QueryParam( "datasourceTableName" ) String datasourceTableName,
                                @QueryParam( "datasourceColumnFamily" ) String datasourceColumnFamily )
    {
        ImportSetting settings = new ImportSetting();

        DataSourceType type = DataSourceType.valueOf( dataSourceType );
        settings.setType( type );

        if ( !Strings.isNullOrEmpty( importAllTables ) )
        {
            settings.addParameter( ImportParameter.IMPORT_ALL_TABLES, importAllTables );
        }

        if ( !Strings.isNullOrEmpty( datasourceDatabase ) )
        {
            settings.addParameter( ImportParameter.DATASOURCE_DATABASE, datasourceDatabase );
        }

        if ( !Strings.isNullOrEmpty( datasourceTableName ) )
        {
            settings.addParameter( ImportParameter.DATASOURCE_TABLE_NAME, datasourceTableName );
        }

        if ( !Strings.isNullOrEmpty( datasourceColumnFamily ) )
        {
            settings.addParameter( ImportParameter.DATASOURCE_COLUMN_FAMILY, datasourceColumnFamily );
        }

        UUID uuid = sqoopManager.importData( settings );

        String operationId = JsonUtil.toJson( OPERATION_ID, uuid );
        return Response.status( Response.Status.CREATED ).entity( operationId ).build();
    }


    @GET
    @Path( "exportData" )
    @Produces( { MediaType.APPLICATION_JSON } )
    public Response exportData( @QueryParam( "hdfsPath" ) String hdfsPath )
    {
        ExportSetting setting = new ExportSetting();
        setting.setHdfsPath( hdfsPath );

        UUID uuid = sqoopManager.exportData( setting );

        String operationId = JsonUtil.toJson( OPERATION_ID, uuid );
        return Response.status( Response.Status.OK ).entity( operationId ).build();
    }
}
