import { Monitor, Moon, Sun, type LucideIcon } from 'lucide-react';
import { setThemePreference, useThemePreference, type ThemePreference } from '../app/theme';
import { messages } from '../messages';
import { IconButton } from './ui/IconButton';
import { Menu, MenuRadioGroup, MenuRadioItem } from './ui/Menu';

const OPTIONS: { value: ThemePreference; icon: LucideIcon }[] = [
  { value: 'system', icon: Monitor },
  { value: 'light', icon: Sun },
  { value: 'dark', icon: Moon },
];

export function ThemeMenu() {
  const preference = useThemePreference();
  const icon = OPTIONS.find((option) => option.value === preference)?.icon ?? Monitor;
  const text = messages.theme;
  return (
    <Menu trigger={<IconButton label={text.label(text.options[preference])} icon={icon} />}>
      <MenuRadioGroup
        value={preference}
        onValueChange={(value) => setThemePreference(value as ThemePreference)}
      >
        {OPTIONS.map((option) => (
          <MenuRadioItem key={option.value} value={option.value} icon={option.icon}>
            {text.options[option.value]}
          </MenuRadioItem>
        ))}
      </MenuRadioGroup>
    </Menu>
  );
}
