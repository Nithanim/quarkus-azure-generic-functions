This Quarkus extension allows Quarkus to be used for Azure Functions with all the ordinary bindings.

> **Warning**
> THIS IS HIGHLY WORK IN PROGRESS

At work I currently have it running and seems to work flawlessly for a simple http trigger.

> **Warning**
> It requires a custom version of https://github.com/Nithanim/azure-maven-plugins where the usage of reflection is replaced by jandex.


LICENSING:
Not sure because the Azure Maven Plugin is MIT and all Quarkus stuff in APACHE 2.
I mean the Azure Maven Plugin stuff is pretty much a dependency only so there should not be a problem.
Though, Quarkus stuff has been copied over (from the existing functions plugin, mostly), so it might be APACHE 2?
Anyway, my stuff is MIT.

Formatter is google.
