package org.safehaus.subutai.plugin.oozie.cli;


import java.util.UUID;

import org.safehaus.subutai.common.environment.ContainerHostNotFoundException;
import org.safehaus.subutai.common.environment.Environment;
import org.safehaus.subutai.common.environment.EnvironmentNotFoundException;
import org.safehaus.subutai.core.env.api.EnvironmentManager;
import org.safehaus.subutai.plugin.oozie.api.Oozie;
import org.safehaus.subutai.plugin.oozie.api.OozieClusterConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.karaf.shell.commands.Argument;
import org.apache.karaf.shell.commands.Command;
import org.apache.karaf.shell.console.OsgiCommandSupport;


/**
 * sample command : oozie:describe-cluster test \ {cluster name}
 */
@Command( scope = "oozie", name = "describe-cluster", description = "Shows the details of the Oozie cluster." )
public class DescribeClusterCommand extends OsgiCommandSupport
{

    @Argument( index = 0, name = "clusterName", description = "The name of the cluster.", required = true,
            multiValued = false )
    String clusterName = null;
    private Oozie oozieManager;
    private EnvironmentManager environmentManager;
    private static final Logger LOG = LoggerFactory.getLogger( DescribeClusterCommand.class.getName() );


    protected Object doExecute()
    {
        OozieClusterConfig config = oozieManager.getCluster( clusterName );
        try
        {
            Environment environment = environmentManager.findEnvironment( config.getEnvironmentId() );
            StringBuilder sb = new StringBuilder();
            sb.append( "Cluster name: " ).append( config.getClusterName() ).append( "\n" );
            sb.append( "Nodes:" ).append( "\n" );
            for ( UUID containerId : config.getAllNodes() )
            {
                try
                {
                    sb.append( "   Container Hostname: " ).
                            append( environment.getContainerHostById( containerId ).getHostname() ).append( "\n" );
                }
                catch ( ContainerHostNotFoundException e )
                {
                    LOG.error( "Could not find container host !!!" );
                    e.printStackTrace();
                }
            }
            sb.append( "Server:" ).append( "\n" );
            try
            {
                sb.append( "   Container Hostname: " ).
                        append( environment.getContainerHostById( config.getServer() ).getHostname() ).append( "\n" );
            }
            catch ( ContainerHostNotFoundException e )
            {
                LOG.error( "Could not find container host !!!" );
                e.printStackTrace();
            }
            sb.append( "Clients :" ).append( "\n" );
            for ( UUID containerId : config.getClients() )
            {
                try
                {
                    sb.append( "   Container Hostname: " ).
                            append( environment.getContainerHostById( containerId ).getHostname() ).append( "\n" );
                }
                catch ( ContainerHostNotFoundException e )
                {
                    LOG.error( "Could not find container host !!!" );
                    e.printStackTrace();
                }
            }
            System.out.println( sb.toString() );
        }
        catch ( EnvironmentNotFoundException e )
        {
            LOG.error( "Could not find environment !!! " );
            e.printStackTrace();
        }

        return null;
    }


    public Oozie getOozieManager()
    {
        return oozieManager;
    }


    public void setOozieManager( final Oozie oozieManager )
    {
        this.oozieManager = oozieManager;
    }


    public EnvironmentManager getEnvironmentManager()
    {
        return environmentManager;
    }


    public void setEnvironmentManager( final EnvironmentManager environmentManager )
    {
        this.environmentManager = environmentManager;
    }
}
