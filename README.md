This Quarkus extension allows Quarkus to be used for Azure Functions with all the ordinary bindings.

> **Warning**
> THIS IS HIGHLY WORK IN PROGRESS

> **Warning**
> RequestScope does currently not work


## How it works

For each class with methods annotated with `@FunctionName` a proxy is generated.
This proxy loads Quarkus and delegates all invocations to your original function.


## LICENSING
Not sure because the Azure Maven Plugin is MIT and all Quarkus stuff in APACHE 2.
I mean the Azure Maven Plugin stuff is pretty much a dependency only so there should not be a problem.
Though, Quarkus stuff has been copied over (from the existing functions plugin, mostly), so it might be APACHE 2?
Anyway, my work is licensed under APACHE 2 to be Quarkus-friendly.

Formatter is google.
