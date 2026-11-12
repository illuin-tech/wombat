package tech.illuin.wombat;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.annotations.QuarkusMain;

import java.util.ArrayList;
import java.util.List;

@QuarkusMain
public class WombatMain
{
    static final String MODE_OPTION = "--mode";
    static final String MODE_PROPERTY = "wombat.mode";

    static void main(String... args)
    {
        Quarkus.run(applyOptions(args));
    }

    static String[] applyOptions(String... args)
    {
        List<String> remaining = new ArrayList<>(args.length);
        for (int i = 0; i < args.length; i++)
        {
            String arg = args[i];
            if (arg.equals(MODE_OPTION))
            {
                if (i + 1 >= args.length)
                    throw new IllegalArgumentException(MODE_OPTION + " requires a value: standalone, aggregator or worker");
                System.setProperty(MODE_PROPERTY, args[++i]);
            }
            else if (arg.startsWith(MODE_OPTION + "="))
                System.setProperty(MODE_PROPERTY, arg.substring(MODE_OPTION.length() + 1));
            else
                remaining.add(arg);
        }
        return remaining.toArray(String[]::new);
    }
}
