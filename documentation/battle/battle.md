# Боёвка

## Боевой персонаж

Характеристики:
- Здоровье (hp)
- Атака (atk)
- Защита (def)
- Сила (strength)
- Ловкость (agility)
- Мудрость (wisdom)

## Бой

### Шанс критического урона
`wisdom1` - мудрость игрока1 <br>
`wisdom2` - мудрость игрока2 <br>

`chance = 50 + (wisdom1 - wisdom2) * 0.5`

`шанс_крита = chance < 10 ? 10 : chance > 90 ? 90 : chance`

### Один ход боя (Игрок1 против Игрок2)

```
rand = random(1, 100)
шанс_попадания = 90 - Игрок2.ловкость * 1.6
если random(1, 100) > шанс_попадания:
    закончить ход (промах)
    
базовый_урон = Игрок1.атака + Игрок1.сила * 1.1 - Игрок2.защита * 0.7

если базовый_урон < Игрок1.атака * 0.1:
    базовый_урон = Игрок1.атака * 0.1

шанс_крита = 90 - Игрок1.мудрость * 2
если random(1, 100) > шанс_крита:
    множитель_крита = 2 + макс(Игрок1.мудрость - Игрок2.ловкость * 0.4, 0) * 0.04
иначе
    множитель_крита = 1

Игрок2.здоровье -= базовый_урон * множитель_крита

если Игрок2.здоровье < 0:
    поражение Игрок2
```



