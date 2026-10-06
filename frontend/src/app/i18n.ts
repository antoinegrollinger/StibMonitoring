import { Injectable, computed, effect, signal } from '@angular/core';
import { LocalizedName, LocalizedText } from './stib.models';

export const LANGUAGES = ['en', 'fr', 'nl'] as const;
export type Lang = (typeof LANGUAGES)[number];

const en = {
  'app.title': 'STIB Monitoring',
  'app.language': 'Language',
  'lines.label': 'Lines',
  'lines.placeholder': 'e.g. 1, 5, 92',
  'lines.add': 'Add',
  'lines.remove': 'Remove line {line}',
  'lines.removeAll': 'Stop showing all lines',
  'lines.all': 'All lines',
  'lines.clear': 'Clear',
  'lines.allCount': 'All lines ({count})',
  'lines.unknown': 'Unknown line: {lines}',
  'sidebar.empty': 'Add a line to get started.',
  'sidebar.vehicles.one': '{count} vehicle',
  'sidebar.vehicles.other': '{count} vehicles',
  'sidebar.expandAll': 'Expand all',
  'sidebar.collapseAll': 'Collapse all',
  'direction.showOnly': 'Show only this direction on the map',
  'direction.showAll': 'Show all directions on the map',
  'status.refreshing': 'Refreshing…',
  'status.summary.one': '{count} vehicle · updated {time}',
  'status.summary.other': '{count} vehicles · updated {time}',
  'error.backend': 'Could not reach the backend. Is it running on port 8080?',
  'error.rateLimit': 'STIB rate limit reached — retrying automatically.',
  'error.noData': 'No data for line {line}.',
  'error.waitingTimes': 'Could not load waiting times.',
  'sidebar.loading': 'Loading…',
  'messages.region': 'Service messages',
  'messages.count.one': '{count} service message',
  'messages.count.other': '{count} service messages',
  'messages.stopFlag': 'Service message for this stop',
  'stop.close': 'Close',
  'stop.loading': 'Loading…',
  'stop.noPassages': 'No upcoming passages.',
  'eta.now': 'now',
  'eta.minutes': '{minutes} min',
  'map.line': 'Line {line}',
  'map.vehicle': 'Vehicle',
  'refresh.label': 'Auto-refresh',
  'refresh.off': 'Off',
  'refresh.seconds': '{seconds} s',
  'refresh.now': 'Refresh now',
  'theme.label': 'Theme',
  'theme.system': 'Match system',
  'theme.light': 'Light',
  'theme.dark': 'Dark',
};

export type TranslationKey = keyof typeof en;

/** Keys that come in `.one` / `.other` variants. */
type PluralKey = { [K in TranslationKey]: K extends `${infer Base}.one` ? Base : never }[TranslationKey];

const TRANSLATIONS: Record<Lang, Record<TranslationKey, string>> = {
  en,
  fr: {
    'app.title': 'STIB Monitoring',
    'app.language': 'Langue',
    'lines.label': 'Lignes',
    'lines.placeholder': 'ex. 1, 5, 92',
    'lines.add': 'Ajouter',
    'lines.remove': 'Retirer la ligne {line}',
    'lines.removeAll': 'Ne plus afficher toutes les lignes',
    'lines.all': 'Toutes les lignes',
    'lines.clear': 'Effacer',
    'lines.allCount': 'Toutes les lignes ({count})',
    'lines.unknown': 'Ligne inconnue : {lines}',
    'sidebar.empty': 'Ajoutez une ligne pour commencer.',
    'sidebar.vehicles.one': '{count} véhicule',
    'sidebar.vehicles.other': '{count} véhicules',
    'sidebar.expandAll': 'Tout déplier',
    'sidebar.collapseAll': 'Tout replier',
    'direction.showOnly': 'Afficher uniquement ce sens sur la carte',
    'direction.showAll': 'Afficher tous les sens sur la carte',
    'status.refreshing': 'Actualisation…',
    'status.summary.one': '{count} véhicule · mis à jour à {time}',
    'status.summary.other': '{count} véhicules · mis à jour à {time}',
    'error.backend': 'Impossible de joindre le backend. Est-il lancé sur le port 8080 ?',
    'error.rateLimit': 'Limite de requêtes STIB atteinte — nouvel essai automatique.',
    'error.noData': 'Aucune donnée pour la ligne {line}.',
    'error.waitingTimes': 'Impossible de charger les temps d’attente.',
    'sidebar.loading': 'Chargement…',
    'messages.region': 'Messages de service',
    'messages.count.one': '{count} message de service',
    'messages.count.other': '{count} messages de service',
    'messages.stopFlag': 'Message de service pour cet arrêt',
    'stop.close': 'Fermer',
    'stop.loading': 'Chargement…',
    'stop.noPassages': 'Aucun passage prévu.',
    'eta.now': 'maintenant',
    'eta.minutes': '{minutes} min',
    'map.line': 'Ligne {line}',
    'map.vehicle': 'Véhicule',
    'refresh.label': 'Actualisation auto',
    'refresh.off': 'Désactivée',
    'refresh.seconds': '{seconds} s',
    'refresh.now': 'Actualiser maintenant',
    'theme.label': 'Thème',
    'theme.system': 'Comme le système',
    'theme.light': 'Clair',
    'theme.dark': 'Sombre',
  },
  nl: {
    'app.title': 'MIVB Monitoring',
    'app.language': 'Taal',
    'lines.label': 'Lijnen',
    'lines.placeholder': 'bv. 1, 5, 92',
    'lines.add': 'Toevoegen',
    'lines.remove': 'Lijn {line} verwijderen',
    'lines.removeAll': 'Niet langer alle lijnen tonen',
    'lines.all': 'Alle lijnen',
    'lines.clear': 'Wissen',
    'lines.allCount': 'Alle lijnen ({count})',
    'lines.unknown': 'Onbekende lijn: {lines}',
    'sidebar.empty': 'Voeg een lijn toe om te beginnen.',
    'sidebar.vehicles.one': '{count} voertuig',
    'sidebar.vehicles.other': '{count} voertuigen',
    'sidebar.expandAll': 'Alles openklappen',
    'sidebar.collapseAll': 'Alles dichtklappen',
    'direction.showOnly': 'Alleen deze richting op de kaart tonen',
    'direction.showAll': 'Alle richtingen op de kaart tonen',
    'status.refreshing': 'Vernieuwen…',
    'status.summary.one': '{count} voertuig · bijgewerkt om {time}',
    'status.summary.other': '{count} voertuigen · bijgewerkt om {time}',
    'error.backend': 'Kan de backend niet bereiken. Draait die op poort 8080?',
    'error.rateLimit': 'MIVB-aanvraaglimiet bereikt — wordt automatisch opnieuw geprobeerd.',
    'error.noData': 'Geen gegevens voor lijn {line}.',
    'error.waitingTimes': 'Kan de wachttijden niet laden.',
    'sidebar.loading': 'Laden…',
    'messages.region': 'Dienstberichten',
    'messages.count.one': '{count} dienstbericht',
    'messages.count.other': '{count} dienstberichten',
    'messages.stopFlag': 'Dienstbericht voor deze halte',
    'stop.close': 'Sluiten',
    'stop.loading': 'Laden…',
    'stop.noPassages': 'Geen geplande doorkomsten.',
    'eta.now': 'nu',
    'eta.minutes': '{minutes} min',
    'map.line': 'Lijn {line}',
    'map.vehicle': 'Voertuig',
    'refresh.label': 'Automatisch vernieuwen',
    'refresh.off': 'Uit',
    'refresh.seconds': '{seconds} s',
    'refresh.now': 'Nu vernieuwen',
    'theme.label': 'Thema',
    'theme.system': 'Zoals systeem',
    'theme.light': 'Licht',
    'theme.dark': 'Donker',
  },
};

/** A message to show later, so it is re-translated if the language changes meanwhile. */
export interface Translatable {
  key: TranslationKey;
  params?: Record<string, string | number>;
}

const STORAGE_KEY = 'stib-monitoring.lang';

@Injectable({ providedIn: 'root' })
export class I18n {
  readonly lang = signal<Lang>(initialLang());

  /** BCP 47 locale for number/time formatting. */
  readonly locale = computed(() => `${this.lang()}-BE`);

  constructor() {
    effect(() => {
      const lang = this.lang();
      document.documentElement.lang = lang;
      try {
        localStorage.setItem(STORAGE_KEY, lang);
      } catch {
        // Storage unavailable (private mode…): the choice just isn't remembered.
      }
    });
  }

  t(key: TranslationKey, params: Record<string, string | number> = {}): string {
    return TRANSLATIONS[this.lang()][key].replace(/\{(\w+)\}/g, (_, name) => String(params[name] ?? `{${name}}`));
  }

  /** Picks the `.one` or `.other` variant using the language's plural rules (French uses "one" for 0 too). */
  plural(key: PluralKey, count: number, params: Record<string, string | number> = {}): string {
    const form = new Intl.PluralRules(this.lang()).select(count) === 'one' ? 'one' : 'other';
    return this.t(`${key}.${form}` as TranslationKey, { ...params, count });
  }

  translate(message: Translatable): string {
    return this.t(message.key, message.params);
  }

  /** STIB names only exist in French and Dutch; English uses the French name, as STIB's own English site does. */
  name(value: LocalizedName | null | undefined): string | null {
    if (!value) {
      return null;
    }
    return this.lang() === 'nl' ? value.nl ?? value.fr : value.fr ?? value.nl;
  }

  /** Free text published in all three languages, falling back to whichever exists. */
  text(value: LocalizedText | null | undefined): string | null {
    if (!value) {
      return null;
    }
    return value[this.lang()] ?? value.en ?? value.fr ?? value.nl;
  }

  time(date: Date): string {
    return date.toLocaleTimeString(this.locale());
  }
}

function initialLang(): Lang {
  try {
    const stored = localStorage.getItem(STORAGE_KEY);
    if (LANGUAGES.includes(stored as Lang)) {
      return stored as Lang;
    }
  } catch {
    // ignore
  }
  const browser = navigator.language.slice(0, 2).toLowerCase();
  return LANGUAGES.includes(browser as Lang) ? (browser as Lang) : 'en';
}
