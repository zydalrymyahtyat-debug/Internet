1. **Change createTextBitmap logic**: Modify `SpeedMonitorService.kt` so that `createTextBitmap` expects a Pair or two parts (number and unit). Draw the number on the top line and the unit (`M`, `K`, `B`) directly underneath it on the second line to make the number larger and prevent clipping.
2. **Change formatSpeedShort logic**: Modify `formatSpeedShort` to return the pair of the number and the unit string.
3. **Run testing**: Run `./gradlew test` and `./gradlew assembleDebug` to make sure changes compile successfully.
4. **Complete pre-commit steps**: Ensure proper testing, verification, review, and reflection are done.
5. **Submit**: Submit the changes.
