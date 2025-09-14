# jedit_interlis_plugin

```
java -jar jedit.jar -log=3
```

```
./gradlew clean build copyPlugin -i && rm ~/Library/jEdit/jars-cache/InterlisPlugin.jar.summary
```

## notes

- tipps:
 * fonts
 * keyboard shortcuts machen
 * rough... ecken und kanten. 
 * error list als dockable alles als dockable empfehlenswert
 * java 21. betrifft aber glaubs nur macos: java -jar  -Xms2g -Xmx2g -Dsun.java2d.metal=false build/jedit.jar -settings=../jEdit/portable-settings wegen metal. ggf -XX:+UseParallelGC
 * Inkonsitzenzen zwischen Export von files (ohne GUI). GUI speichert letztes Verzeichnis nciht.
 * ...

- Jedes Feature beschreiben und dann 23Fr.-Kolleg die Arbeit machen lassen.
- Beginnen mit Theme und Fonts (auch line numbering)
- syntax highlighing 



## todo

- ~~Parsing von TransferDescriptioin vereinheitlichen (Sidekick und UML)~~
- ~~meta info in props~~
- ~~dependency handling?~~
- Keyboard shortcut (default): Geht bei mir noch nicht. Ist es überhaupt sinnvoll? Oder soll man das den Benutzer entscheiden lassen.
- ~~activate plugin only when .ili file in current buffer?~~
- ~~console output?~~ 
- ~~error list?~~ 
-~~ toggle (is_selected)~~
- options: ~~ilidirs!!~~
- ~~Fokus sollte nach kompilieren nicht in der Shell sein.~~
- outline/tree: icons (was hat umleditor?)
- outline/tree: ~~~imported models~~ (sonst noch was sinnvolles?).
- ~~outline/tree: fehlermeldung in der konsole?~~
- ~~cache für td~~
- ~~autocomplete: INTERLIS 2.4 <shift+enter> -> Kommentare~~
- ~~autocomplete: MODEL .. <shift+enter> -> oberhalb die Metattribute~~
- autocomplete: ~~TOPIC, CLASS,~~ ASSOCIATION
- Deployment? Wohin? Automatisch?
- ~~Default indent size: https://chat.qwen.ai/c/7e6814f6-b48d-4678-84e1-78e8cea0fe37~~
- ~~Edit: Modelle zum Importieren vorschlagen~~ 
- Hyperlink: Auch für Topics, Klassen etc. (weiss nicht wie schwierig das ist).
- ~~UML: als dockable window.~~
- ~~UML: vererbte Attribute~~
- ~~UML: Attributtypen~~
- UML: Ein-/Ausschalten Attributtypen und Kardinalität
- UML: ~~Kardinalität~~
- UML: Menu, ~~Zoom, Pan~~
- UML: Option "show all attributes"
- UML: Refresh mit Platzierung beibehalten
- ~~UML: Sync~~
- ~~UML: stereotype~~
- ~~UML: Strukturen~~
- ~~UML: topics~~
- ~~UML: Vererbungen~~
- ~~UML: Assoziationen~~
- ~~UML: Abstrakt kursiv~~ -> mit Stereotyp
- Modell: Objektkatalog als Word.
- UML: PNG/SVG-Export
- ~~UML: Einfärben~~ -> Momentan jede Klasse separat. -> Option mit "Alle Klassen Einfärben"
- UML: "Optimale" Platzierung Algo
- ~~UML: show inherited attributes in different format (also Option).~~ tendenziell nicht. backlog?
- Clean code / Refactoring: Trennen von UmlDockable und Drawings?
- Clean code / Refactoring: Was von der Zeichnung persistieren? Was soll nach einem Refresh beibehalten werden? Kann man Zeichnung 
irgendwie sinnvoll separat speichern? Oder ggf. doch ilizip-Format?
- Clean code / Refactoring: Infos aus TD zusammensuchen könnte/sollte man konsolidieren. Es wird an verschiedenen Orten mehrfach gemacht.