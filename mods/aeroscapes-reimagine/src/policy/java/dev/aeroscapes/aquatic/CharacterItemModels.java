package dev.aeroscapes.aquatic;

import static dev.aeroscapes.aquatic.EquipmentObjMesh.octagonalRing;

import dev.aeroscapes.aquatic.EquipmentObjMesh.P;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

final class CharacterItemModels {
  private CharacterItemModels() {}

  static void add(Path assets, Map<Path, byte[]> output) {
    Path models = assets.resolve("aeroscapes_reimagine/models/item/character");
    EquipmentObjMesh seal = new EquipmentObjMesh();
    seal.group("die_and_stem");
    seal.shell(
        "bronze",
        new P[][] {
          octagonalRing(8, 4, 8, 3.25, 2.8),
          octagonalRing(8, 4.45, 8, 3.45, 3),
          octagonalRing(8, 5.15, 8, 3.35, 2.95),
          octagonalRing(8, 5.65, 8, 2.8, 2.45),
          octagonalRing(8, 6, 8, 1.85, 1.7),
          octagonalRing(8, 7.2, 8, 1.05, 1),
          octagonalRing(8, 9.9, 8, 0.72, 0.7),
          octagonalRing(8, 10.15, 8, 1.03, 0.83),
          octagonalRing(8, 10.5, 8, 1.0, 0.8)
        });
    seal.group("three_forked_impression");
    seal.box("brass", 7.76, 3.83, 6.6, 8.24, 4.04, 9.55);
    seal.beam("brass", new P(8, 3.97, 8.05), new P(6.7, 3.97, 7.18), 0.2, 4);
    seal.beam("brass", new P(8, 3.97, 8.65), new P(9.32, 3.97, 7.68), 0.2, 4);
    seal.group("suspension_eye");
    seal.tube(
        "brass",
        List.of(
            new P(8, 10.42, 8),
            new P(7.15, 10.8, 8),
            new P(6.87, 11.75, 8),
            new P(7.28, 12.5, 8),
            new P(8, 12.75, 8),
            new P(8.72, 12.5, 8),
            new P(9.13, 11.75, 8),
            new P(8.85, 10.8, 8),
            new P(8, 10.42, 8)),
        0.36,
        6,
        3);
    EquipmentModels.text(output, models.resolve("identity_seal.obj"), seal.obj());

    EquipmentObjMesh thread = new EquipmentObjMesh();
    thread.group("knotted_cord");
    // The lower ends are free. The cord passes in front of the pearl and returns behind it.
    thread.tube(
        "linen",
        List.of(
            new P(6.5, 3.25, 8.7),
            new P(5.95, 4, 8.5),
            new P(6.65, 5.45, 8.3),
            new P(7.8, 6.9, 8.65),
            new P(9.45, 8.55, 8.9),
            new P(10.15, 10.3, 8.35),
            new P(9.75, 11.75, 8),
            new P(8.5, 12.35, 7.85),
            new P(7.15, 11.7, 7.8),
            new P(6.65, 10.2, 7.9),
            new P(7, 8.8, 8.65),
            new P(8.3, 7.95, 9.42),
            new P(9.6, 7.55, 9.05),
            new P(10.1, 8.05, 7.95),
            new P(9.2, 8.65, 7.05),
            new P(7.65, 8.35, 7.05),
            new P(7.5, 7.5, 7.65),
            new P(8.6, 6.65, 7.75),
            new P(9.35, 5.7, 7.65),
            new P(10.35, 5.25, 7.5)),
        0.48,
        6,
        2.5);
    thread.group("pearl");
    thread.shell(
        "pearl",
        new P[][] {
          octagonalRing(8.35, 7.5, 8.15, 0.55, 0.55),
          octagonalRing(8.35, 8.1, 8.15, 1.08, 1.12),
          octagonalRing(8.35, 8.95, 8.15, 1.12, 1.13),
          octagonalRing(8.35, 9.55, 8.15, 0.65, 0.68)
        });
    EquipmentModels.text(output, models.resolve("knotted_thread.obj"), thread.obj());
  }
}
