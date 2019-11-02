package Server;
import akka.actor.*;


public class Server
{
    public static void main(String[] args)
    {//Create the system, the serverActor
        ActorSystem system = ActorSystem.create("System");
        ActorRef server = system.actorOf(Props.create(ServerActor.class), "Server");
    }
}
