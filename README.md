# jedit_interlis_plugin

```
java -jar jedit.jar -log=3
```

```
./gradlew clean build && cp build/libs/InterlisPlugin.jar ~/Library/jEdit/jars
```


## todo

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
- outline/tree: imported models (sonst noch was sinnvolles?).
- ~~outline/tree: fehlermeldung in der konsole?~~
- cache für td
- autocomplete: INTERLIS 2.4 <shift+enter> -> Kommentare
- autocomplete: MODEL .. <shift+enter> -> oberhalb die Metattribute
- autocomplete: TOPIC, CLASS, ASSOCIATION
- Deployment? Wohin? Automatisch?