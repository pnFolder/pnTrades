# pnTrades

Плагин безопасного обмена предметами между игроками через GUI для Paper и Purpur 1.16.5+.

[![Release](https://img.shields.io/github/v/release/pnFolder/pnTrades?label=Release)](https://github.com/pnFolder/pnTrades/releases/latest)
[![License](https://img.shields.io/github/license/pnFolder/pnTrades?label=License)](LICENSE)

## Возможности

- Двусторонний обмен до 16 слотов с каждой стороны.
- Кликабельные кнопки принятия и отказа в сообщении запроса.
- Подтверждение обоих игроков; оно сбрасывается при изменении предметов.
- Возврат предметов при закрытии GUI, выходе игрока или выключении сервера.
- Настраиваемый интерфейс через `gui.yml`.
- Проверка обновлений и политика запуска через pnLibrary (устанавливается автоматически).

## Установка

1. Скачайте `pnTrades-1.0.0.jar` из [релизов](https://github.com/pnFolder/pnTrades/releases/latest).
2. Поместите JAR в папку `plugins` сервера.
3. Перезапустите сервер.
4. Настройте `plugins/pnTrades/config.yml` и `plugins/pnTrades/gui.yml` при необходимости.

Требуется Java 17 или новее.

## Команды

| Команда | Описание |
| --- | --- |
| `/trade <игрок>` | Отправить предложение обмена. |
| `/trade accept <игрок>` | Принять предложение. |
| `/trade deny <игрок>` | Отклонить предложение. |

Алиасы: `/trades`, `/обмен`.

## Права

| Право | Описание | По умолчанию |
| --- | --- | --- |
| `pntrades.use` | Использование обмена. | Все игроки |
| `pntrades.admin` | Уведомления о новых версиях. | OP |

## Настройка GUI

Каждый элемент в `gui.yml` использует понятные поля:

```yml
buttons:
  self:
    no:
      name: '&aПодтвердить обмен'
      material: LIME_WOOL
      slot: 38
      lore:
        - '&7Нажмите, когда предложение готово.'
```

- `name` - название предмета.
- `material` - Bukkit Material.
- `slot` - слот кнопки в GUI.
- `lore` - список строк описания.

`title` поддерживает `{player}` для имени второго участника обмена. Цвета задаются через `&`.

## Сборка

```text
gradlew build
```

Собранный JAR будет находиться в `build/libs`.

## Лицензия

Проект распространяется по [MIT License](LICENSE).
